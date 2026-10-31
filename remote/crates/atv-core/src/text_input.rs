//! Remote Text Input: the iPhone system keyboard types into the TV.
//!
//! Archive layout follows pyatv (`keyed_archiver`, `rti_text_operations`).

use std::io::Cursor;
use std::sync::{Mutex, OnceLock};

use plist::{Dictionary, Uid, Value as Plist};
use tokio::sync::broadcast;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TextFocusEvent {
    Started,
    Stopped,
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct TextOperations {
    pub insert: Option<String>,
    pub delete_backward: u64,
    pub replace_all: Option<String>,
}

struct State {
    focused: bool,
    session_uuid: [u8; 16],
    text: String,
}

fn state() -> &'static Mutex<State> {
    static STATE: OnceLock<Mutex<State>> = OnceLock::new();
    STATE.get_or_init(|| {
        Mutex::new(State {
            focused: false,
            session_uuid: [0; 16],
            text: String::new(),
        })
    })
}

fn channel() -> &'static broadcast::Sender<TextFocusEvent> {
    static CHANNEL: OnceLock<broadcast::Sender<TextFocusEvent>> = OnceLock::new();
    CHANNEL.get_or_init(|| broadcast::channel(16).0)
}

pub fn subscribe() -> broadcast::Receiver<TextFocusEvent> {
    channel().subscribe()
}

pub fn set_focus(focused: bool, text: &str) {
    let changed = {
        let mut current = state().lock().unwrap();
        let changed = current.focused != focused;
        if focused && changed {
            current.session_uuid = *uuid::Uuid::new_v4().as_bytes();
        }
        current.focused = focused;
        current.text = text.to_string();
        changed
    };
    if changed {
        let event = if focused {
            TextFocusEvent::Started
        } else {
            TextFocusEvent::Stopped
        };
        let _ = channel().send(event);
    }
}

pub fn current_archive() -> Option<Vec<u8>> {
    let current = state().lock().unwrap();
    current
        .focused
        .then(|| build_session_archive(&current.session_uuid, &current.text))
}

fn dict(entries: Vec<(&str, Plist)>) -> Plist {
    let mut out = Dictionary::new();
    for (key, value) in entries {
        out.insert(key.to_string(), value);
    }
    Plist::Dictionary(out)
}

fn uid(index: u64) -> Plist {
    Plist::Uid(Uid::new(index))
}

fn class(name: &str) -> Plist {
    dict(vec![
        ("$classname", Plist::String(name.to_string())),
        (
            "$classes",
            Plist::Array(vec![
                Plist::String(name.to_string()),
                Plist::String("NSObject".to_string()),
            ]),
        ),
    ])
}

pub fn build_session_archive(session_uuid: &[u8; 16], text: &str) -> Vec<u8> {
    let objects = vec![
        Plist::String("$null".to_string()),
        Plist::Data(session_uuid.to_vec()),
        dict(vec![("docSt", uid(3)), ("$class", uid(6))]),
        dict(vec![("contextBeforeInput", uid(4)), ("$class", uid(5))]),
        Plist::String(text.to_string()),
        class("TIDocumentState"),
        class("RTIDocumentState"),
    ];
    let root = dict(vec![
        ("$version", Plist::Integer(100000.into())),
        ("$archiver", Plist::String("RTIKeyedArchiver".to_string())),
        (
            "$top",
            dict(vec![("sessionUUID", uid(1)), ("documentState", uid(2))]),
        ),
        ("$objects", Plist::Array(objects)),
    ]);
    let mut out = Vec::new();
    root.to_writer_binary(&mut out)
        .expect("writing a plist to memory cannot fail");
    out
}

fn resolve<'a>(objects: &'a [Plist], value: &'a Plist) -> &'a Plist {
    match value {
        Plist::Uid(index) => objects.get(index.get() as usize).unwrap_or(value),
        other => other,
    }
}

pub fn parse_operations(data: &[u8]) -> Option<TextOperations> {
    let root = Plist::from_reader(Cursor::new(data)).ok()?;
    let root = root.as_dictionary()?;
    let objects = root.get("$objects")?.as_array()?;
    let top = root.get("$top")?.as_dictionary()?;
    let operations = resolve(objects, top.get("textOperations")?).as_dictionary()?;

    let mut parsed = TextOperations::default();
    if let Some(output) = operations
        .get("keyboardOutput")
        .map(|v| resolve(objects, v))
        .and_then(Plist::as_dictionary)
    {
        parsed.insert = output
            .get("insertionText")
            .map(|v| resolve(objects, v))
            .and_then(Plist::as_string)
            .map(str::to_string);
        parsed.delete_backward = output
            .get("deletionCount")
            .map(|v| resolve(objects, v))
            .and_then(Plist::as_unsigned_integer)
            .unwrap_or(0);
    }
    parsed.replace_all = operations
        .get("textToAssert")
        .map(|v| resolve(objects, v))
        .and_then(Plist::as_string)
        .map(str::to_string);
    Some(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn archive(top: Plist, objects: Vec<Plist>) -> Vec<u8> {
        let root = dict(vec![("$top", top), ("$objects", Plist::Array(objects))]);
        let mut out = Vec::new();
        root.to_writer_binary(&mut out).unwrap();
        out
    }

    #[test]
    fn parses_inserted_text_in_pyatv_layout() {
        let data = archive(
            dict(vec![("textOperations", uid(1))]),
            vec![
                Plist::String("$null".to_string()),
                dict(vec![("keyboardOutput", uid(2))]),
                dict(vec![("insertionText", uid(3))]),
                Plist::String("привет".to_string()),
            ],
        );
        let parsed = parse_operations(&data).unwrap();
        assert_eq!(parsed.insert.as_deref(), Some("привет"));
        assert_eq!(parsed.replace_all, None);
    }

    #[test]
    fn parses_clear_as_replace_with_empty_text() {
        let data = archive(
            dict(vec![("textOperations", uid(1))]),
            vec![
                Plist::String("$null".to_string()),
                dict(vec![("keyboardOutput", uid(2)), ("textToAssert", uid(3))]),
                dict(vec![]),
                Plist::String(String::new()),
            ],
        );
        let parsed = parse_operations(&data).unwrap();
        assert_eq!(parsed.insert, None);
        assert_eq!(parsed.replace_all.as_deref(), Some(""));
    }

    #[test]
    fn session_archive_exposes_uuid_and_text_like_pyatv_reads_them() {
        let data = build_session_archive(&[7; 16], "abc");
        let root = Plist::from_reader(Cursor::new(&data)).unwrap();
        let root = root.as_dictionary().unwrap();
        let objects = root.get("$objects").unwrap().as_array().unwrap();
        let top = root.get("$top").unwrap().as_dictionary().unwrap();
        let session = resolve(objects, top.get("sessionUUID").unwrap());
        assert_eq!(session.as_data().unwrap(), &[7; 16]);
        let document = resolve(objects, top.get("documentState").unwrap())
            .as_dictionary()
            .unwrap();
        let state = resolve(objects, document.get("docSt").unwrap())
            .as_dictionary()
            .unwrap();
        let context = resolve(objects, state.get("contextBeforeInput").unwrap());
        assert_eq!(context.as_string().unwrap(), "abc");
    }
}
