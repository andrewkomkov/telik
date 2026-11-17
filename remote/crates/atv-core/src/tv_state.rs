//! State that the TV app pushes to all remote sessions (spec 006 US1, US2):
//! the launchable apps and the media control flags.
//!
//! Flag and command values follow pyatv (`protocols/companion/api.py`,
//! `MediaControlFlags` and `MediaControlCommand`).

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};

use tokio::sync::broadcast;

pub mod flags {
    pub const PLAY: u64 = 0x0001;
    pub const PAUSE: u64 = 0x0002;
    pub const NEXT: u64 = 0x0004;
    pub const PREVIOUS: u64 = 0x0008;
    pub const FAST_FORWARD: u64 = 0x0010;
    pub const REWIND: u64 = 0x0020;
    pub const VOLUME: u64 = 0x0100;
    pub const SKIP_FORWARD: u64 = 0x0200;
    pub const SKIP_BACKWARD: u64 = 0x0400;
}

/// One launchable app: the id is the Android package name.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct App {
    pub id: String,
    pub name: String,
}

fn apps_cell() -> &'static Mutex<Vec<App>> {
    static APPS: OnceLock<Mutex<Vec<App>>> = OnceLock::new();
    APPS.get_or_init(|| Mutex::new(Vec::new()))
}

static MEDIA_FLAGS: AtomicU64 = AtomicU64::new(flags::VOLUME);

fn airplay_id_cell() -> &'static Mutex<Option<String>> {
    static ID: OnceLock<Mutex<Option<String>>> = OnceLock::new();
    ID.get_or_init(|| Mutex::new(None))
}

/// 008: the `deviceid` of the AirPlay receiver of the same TV. The companion TXT carries it as
/// `rpMRtID`, so iOS can tie the remote to the AirPlay route (its log: "no Airplay ID").
pub fn set_airplay_id(id: Option<String>) {
    *airplay_id_cell().lock().unwrap() = id.filter(|s| !s.is_empty());
}

pub fn airplay_id() -> Option<String> {
    airplay_id_cell().lock().unwrap().clone()
}

fn channel() -> &'static broadcast::Sender<u64> {
    static CHANNEL: OnceLock<broadcast::Sender<u64>> = OnceLock::new();
    CHANNEL.get_or_init(|| broadcast::channel(16).0)
}

/// Replaces the app list. Entries with an empty id are dropped.
pub fn set_apps(apps: Vec<App>) {
    let clean = apps.into_iter().filter(|a| !a.id.is_empty()).collect();
    *apps_cell().lock().unwrap() = clean;
}

pub fn apps() -> Vec<App> {
    apps_cell().lock().unwrap().clone()
}

/// `_launchApp` may open only an app from the list (spec 006 FR-002).
pub fn is_launchable(id: &str) -> bool {
    !id.is_empty() && apps_cell().lock().unwrap().iter().any(|a| a.id == id)
}

/// Sets the media flags. Volume stays on: the TV always takes volume.
/// Returns true when the flags changed; the sessions then get a push.
pub fn set_media_flags(value: u64) -> bool {
    let value = value | flags::VOLUME;
    let old = MEDIA_FLAGS.swap(value, Ordering::SeqCst);
    if old != value {
        let _ = channel().send(value);
        true
    } else {
        false
    }
}

pub fn media_flags() -> u64 {
    MEDIA_FLAGS.load(Ordering::SeqCst)
}

pub fn subscribe() -> broadcast::Receiver<u64> {
    channel().subscribe()
}

/// The button name for a media command from the iPhone, or None for other commands
/// (volume and captions have their own handlers).
pub fn command_button(command: u64) -> Option<&'static str> {
    match command {
        1 => Some("media_play"),
        2 => Some("media_pause"),
        3 => Some("media_next"),
        4 => Some("media_previous"),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // One test for the global state: the tests of a crate share it.
    #[test]
    fn apps_and_flags() {
        set_apps(vec![
            App { id: "com.google.android.youtube.tv".into(), name: "YouTube".into() },
            App { id: String::new(), name: "Broken".into() },
        ]);
        assert_eq!(apps().len(), 1);
        assert!(is_launchable("com.google.android.youtube.tv"));
        assert!(!is_launchable("com.example.other"));
        assert!(!is_launchable(""));

        set_media_flags(0);
        assert_eq!(media_flags(), flags::VOLUME);
        let mut rx = subscribe();
        assert!(set_media_flags(flags::PLAY | flags::PAUSE));
        assert_eq!(media_flags(), flags::PLAY | flags::PAUSE | flags::VOLUME);
        assert_eq!(rx.try_recv().unwrap(), flags::PLAY | flags::PAUSE | flags::VOLUME);
        assert!(!set_media_flags(flags::PLAY | flags::PAUSE));
    }

    #[test]
    fn media_commands() {
        assert_eq!(command_button(1), Some("media_play"));
        assert_eq!(command_button(2), Some("media_pause"));
        assert_eq!(command_button(3), Some("media_next"));
        assert_eq!(command_button(4), Some("media_previous"));
        assert_eq!(command_button(5), None);
        assert_eq!(command_button(6), None);
    }
}
