#![allow(clippy::manual_is_multiple_of)]
#![allow(clippy::if_same_then_else)]
#![allow(unsafe_op_in_unsafe_fn)]

mod board;
mod evaluation;
mod history;
mod lookup;
mod misc;
mod movepick;
mod nnue;
mod numa;
mod parameters;
mod search;
mod stack;
mod thread;
mod threadpool;
mod time;
mod tools;
mod transposition;
mod types;
mod uci;

#[cfg(feature = "syzygy")]
mod tb;

#[cfg(feature = "syzygy")]
#[allow(warnings)]
mod bindings;

use std::sync::Once;

static ENGINE_GLOBALS: Once = Once::new();

/**
 * Integration-only entry point. Upstream's standalone executable initializes its process-global
 * lookup/NNUE tables exactly once before entering the UCI loop. An isolated LumenChess host may
 * close and reopen sessions without restarting the process, so preserve that upstream lifecycle
 * by initializing those globals once per host process and starting a fresh UCI loop per session.
 */
#[unsafe(no_mangle)]
pub extern "C" fn lumen_reckless09_run() -> std::os::raw::c_int {
    ENGINE_GLOBALS.call_once(|| {
        lookup::initialize();
        nnue::initialize();
    });
    uci::message_loop(std::collections::VecDeque::<String>::new());
    0
}
