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

/** Integration-only entry point. Engine initialization and UCI loop are exact v0.9.0 modules. */
#[unsafe(no_mangle)]
pub extern "C" fn lumen_reckless09_run() -> std::os::raw::c_int {
    lookup::initialize();
    nnue::initialize();
    uci::message_loop(std::collections::VecDeque::<String>::new());
    0
}
