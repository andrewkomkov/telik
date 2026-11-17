//! The now playing card of the iOS remote (spec 008 US1).
//!
//! iOS reads it only from a pushed `NowPlayingInfo` event: `_c.NowPlayingInfoKey` holds an
//! NSKeyedArchiver archive of `TVRCNowPlayingInfo` (TVRemoteCore of iOS 26.1). The layout follows a
//! real Apple TV archive (pyatv issue 2461). Titles never go to a log (006 FR-013).

use std::sync::{Mutex, OnceLock};

use plist::{Dictionary, Uid, Value as Plist};
use tokio::sync::broadcast;

/// What plays on the TV, as the TV app gives it.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct NowPlaying {
    pub title: String,
    pub subtitle: String,
    /// The Android package of the player.
    pub bundle: String,
    pub duration_s: f64,
    pub elapsed_s: f64,
    pub playing: bool,
    pub jpeg: Option<Vec<u8>>,
}

/// TVRCNowPlayingInfo playbackState values.
const STATE_PLAYING: i64 = 1;
const STATE_PAUSED: i64 = 2;
const STATE_STOPPED: i64 = 3;
/// Seconds between 1970-01-01 and 2001-01-01: CFAbsoluteTime starts at 2001.
const CF_EPOCH: f64 = 978_307_200.0;

fn cell() -> &'static Mutex<Option<NowPlaying>> {
    static NP: OnceLock<Mutex<Option<NowPlaying>>> = OnceLock::new();
    NP.get_or_init(|| Mutex::new(None))
}

fn channel() -> &'static broadcast::Sender<()> {
    static CH: OnceLock<broadcast::Sender<()>> = OnceLock::new();
    CH.get_or_init(|| broadcast::channel(16).0)
}

pub fn subscribe() -> broadcast::Receiver<()> {
    channel().subscribe()
}

/// Sets what plays, or None when nothing plays. Connected remotes get a push.
pub fn set(np: Option<NowPlaying>) {
    *cell().lock().unwrap() = np;
    let _ = channel().send(());
}

pub fn current() -> Option<NowPlaying> {
    cell().lock().unwrap().clone()
}

fn dict(entries: Vec<(&str, Plist)>) -> Plist {
    let mut out = Dictionary::new();
    for (key, value) in entries {
        out.insert(key.to_string(), value);
    }
    Plist::Dictionary(out)
}

fn uid(i: u64) -> Plist {
    Plist::Uid(Uid::new(i))
}

fn class(name: &str) -> Plist {
    dict(vec![
        ("$classname", Plist::String(name.to_string())),
        ("$classes", Plist::Array(vec![Plist::String(name.to_string()), Plist::String("NSObject".to_string())])),
    ])
}

/// The archive for `NowPlayingInfoKey`. [now_unix] is the time of [np.elapsed_s], in Unix seconds.
pub fn archive(np: Option<&NowPlaying>, now_unix: f64) -> Vec<u8> {
    let mut objects: Vec<Plist> = vec![Plist::String("$null".to_string())];
    let mut push = |v: Plist| -> Plist {
        objects.push(v);
        uid((objects.len() - 1) as u64)
    };
    let root = match np {
        None => {
            // No player: playbackState stop and no metadata, so the card hides.
            let state = push(Plist::Integer(STATE_STOPPED.into()));
            let rate = push(Plist::Real(0.0));
            let info_class = push(class("TVRCNowPlayingInfo"));
            dict(vec![("playbackState", state), ("playbackRate", rate), ("$class", info_class)])
        }
        Some(np) => {
            let identifier = push(Plist::String(format!("dev.telik.player.{}", np.bundle)));
            let player = push(Plist::String(format!("telik-{}", np.bundle)));
            let rate = push(Plist::Real(if np.playing { 1.0 } else { 0.0 }));
            let state = push(Plist::Integer((if np.playing { STATE_PLAYING } else { STATE_PAUSED }).into()));
            let title = push(Plist::String(np.title.clone()));
            let subtitle = push(Plist::String(np.subtitle.clone()));
            let bundle = push(Plist::String(np.bundle.clone()));
            let duration = push(Plist::Real(np.duration_s.max(0.0)));
            let elapsed = push(Plist::Real(np.elapsed_s.max(0.0)));
            let stamp = push(Plist::Real(now_unix - CF_EPOCH));
            let canonical = push(Plist::String("com.apple.tvremote.canonical-identifier.unknown".to_string()));
            let meta_class = push(class("TVRCNowPlayingMetadata"));
            let mut meta = vec![
                ("title", title),
                ("bundleID", bundle),
                ("canonicalID", canonical),
                ("timestamp", stamp),
                ("isAppleOriginal", Plist::Boolean(false)),
                ("kind", Plist::Integer(0.into())),
                ("$class", meta_class),
            ];
            if !np.subtitle.is_empty() {
                meta.push(("episodeTitle", subtitle));
            }
            if np.duration_s > 0.0 {
                meta.push(("duration", duration));
                meta.push(("timeOffset", elapsed));
            }
            let metadata = push(dict(meta));
            let image = np.jpeg.as_ref().map(|j| push(Plist::Data(j.clone())));
            let no = push(Plist::Boolean(false));
            let info_class = push(class("TVRCNowPlayingInfo"));
            let mut info = vec![
                ("identifier", identifier),
                ("playerIdentifier", player),
                ("playbackRate", rate),
                ("playbackState", state),
                ("metadata", metadata),
                ("imageDataIsPlaceholder", no.clone()),
                ("captionsEnabled", no.clone()),
                ("hasValidCaptionOptions", no.clone()),
                ("expectsTimedMetadata", no),
                ("$class", info_class),
            ];
            if let Some(image) = image {
                info.push(("imageData", image));
            }
            dict(info)
        }
    };
    objects.insert(1, root);
    // The root went in at index 1, so each reference past 0 moves up by one.
    let objects: Vec<Plist> = objects.into_iter().map(shift).collect();
    let top = dict(vec![
        ("$archiver", Plist::String("NSKeyedArchiver".to_string())),
        ("$version", Plist::Integer(100000.into())),
        ("$top", dict(vec![("root", uid(1))])),
        ("$objects", Plist::Array(objects)),
    ]);
    let mut out = Vec::new();
    top.to_writer_binary(&mut out).expect("writing a plist to memory cannot fail");
    out
}

/// Moves each UID past 0 ($null) up by one, after the insert of the root at index 1.
fn shift(v: Plist) -> Plist {
    match v {
        Plist::Uid(u) if u.get() > 0 => uid(u.get() + 1),
        Plist::Dictionary(d) => Plist::Dictionary(d.into_iter().map(|(k, v)| (k, shift(v))).collect()),
        Plist::Array(a) => Plist::Array(a.into_iter().map(shift).collect()),
        other => other,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn decode(bytes: &[u8]) -> (Vec<Plist>, Plist) {
        let v = Plist::from_reader(std::io::Cursor::new(bytes)).unwrap();
        let d = v.into_dictionary().unwrap();
        assert_eq!(d.get("$archiver").and_then(|x| x.as_string()), Some("NSKeyedArchiver"));
        let objects = d.get("$objects").unwrap().as_array().unwrap().clone();
        let root_uid = d.get("$top").unwrap().as_dictionary().unwrap().get("root").unwrap().clone();
        let root = match root_uid {
            Plist::Uid(u) => objects[u.get() as usize].clone(),
            _ => panic!("root is not a UID"),
        };
        (objects, root)
    }

    fn get<'a>(objects: &'a [Plist], d: &'a Dictionary, key: &str) -> &'a Plist {
        match d.get(key).unwrap() {
            Plist::Uid(u) => &objects[u.get() as usize],
            other => other,
        }
    }

    fn class_name(objects: &[Plist], d: &Dictionary) -> String {
        get(objects, d, "$class").as_dictionary().unwrap().get("$classname").unwrap().as_string().unwrap().to_string()
    }

    #[test]
    fn archive_of_playing_media() {
        let np = NowPlaying {
            title: "Title".into(),
            subtitle: "Episode".into(),
            bundle: "com.google.android.youtube.tv".into(),
            duration_s: 600.0,
            elapsed_s: 30.0,
            playing: true,
            jpeg: Some(vec![0xFF, 0xD8, 0xFF]),
        };
        let (objects, root) = decode(&archive(Some(&np), CF_EPOCH + 100.0));
        let info = root.as_dictionary().unwrap();
        assert_eq!(class_name(&objects, info), "TVRCNowPlayingInfo");
        assert_eq!(get(&objects, info, "playbackState").as_signed_integer(), Some(1));
        assert_eq!(get(&objects, info, "playbackRate").as_real(), Some(1.0));
        assert_eq!(get(&objects, info, "imageData").as_data(), Some(&[0xFF, 0xD8, 0xFF][..]));
        let meta = get(&objects, info, "metadata").as_dictionary().unwrap();
        assert_eq!(class_name(&objects, meta), "TVRCNowPlayingMetadata");
        assert_eq!(get(&objects, meta, "title").as_string(), Some("Title"));
        assert_eq!(get(&objects, meta, "episodeTitle").as_string(), Some("Episode"));
        assert_eq!(get(&objects, meta, "duration").as_real(), Some(600.0));
        assert_eq!(get(&objects, meta, "timeOffset").as_real(), Some(30.0));
        assert_eq!(get(&objects, meta, "timestamp").as_real(), Some(100.0));
        assert_eq!(objects[0].as_string(), Some("$null"));
    }

    #[test]
    fn archive_without_media_stops() {
        let (objects, root) = decode(&archive(None, CF_EPOCH));
        let info = root.as_dictionary().unwrap();
        assert_eq!(class_name(&objects, info), "TVRCNowPlayingInfo");
        assert_eq!(get(&objects, info, "playbackState").as_signed_integer(), Some(3));
        assert!(info.get("metadata").is_none());
    }

    #[test]
    fn paused_media() {
        let np = NowPlaying { title: "T".into(), playing: false, ..Default::default() };
        let (objects, root) = decode(&archive(Some(&np), CF_EPOCH));
        let info = root.as_dictionary().unwrap();
        assert_eq!(get(&objects, info, "playbackState").as_signed_integer(), Some(2));
        assert_eq!(get(&objects, info, "playbackRate").as_real(), Some(0.0));
        assert!(info.get("imageData").is_none());
    }
}
