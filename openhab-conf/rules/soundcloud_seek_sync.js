// SoundCloud Seek (percent) — optional, for the progress bar of soundcloud_nowplaying
// Triggers: Item State Change on SC_Elapsed, Item Command on SC_SeekPct
//
// Keeps SC_SeekPct (0–100) in sync with the elapsed time and converts a dragged percentage
// back into seconds for player#elapsed-time, which makes the binding seek.
var ELAPSED = 'SC_Elapsed';
var DURATION = 'SC_Duration';
var SEEK_PCT = 'SC_SeekPct';

var duration = parseFloat(items.getItem(DURATION).state) || 0;
if (duration > 0) {
  if (event.itemName === ELAPSED) {
    var elapsed = parseFloat(items.getItem(ELAPSED).state) || 0;
    items.getItem(SEEK_PCT).postUpdate(String(Math.min(100, Math.round(elapsed / duration * 100))));
  } else if (event.itemName === SEEK_PCT) {
    var pct = parseFloat(event.receivedCommand.toString()) || 0;
    var target = Math.round(pct / 100 * duration);
    items.getItem(ELAPSED).sendCommand(String(target));
    console.info('SoundCloud seek: ' + pct + '% = ' + target + ' s');
  }
}
