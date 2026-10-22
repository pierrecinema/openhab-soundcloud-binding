// SoundCloud Auto-Play
// Trigger: Item State Change on SC_StreamURL (item linked to player#stream-url)
//
// Fires whenever a new track is loaded (search result, next / previous, playlist, repeat)
// and sends the new stream URL to the selected Chromecast playuri item.
// Pause / resume do not change the URL, so they never restart the track.
var streamUrl = String(items.getItem('SC_StreamURL').state);
var target = String(items.getItem('SC_CCTarget').state);

if (streamUrl.indexOf('http') !== 0) {
  console.debug('SoundCloud AutoPlay: no stream URL, aborted');
} else if (!target || target === 'NULL' || target === '') {
  console.debug('SoundCloud AutoPlay: no Chromecast target, aborted');
} else {
  console.info('SoundCloud AutoPlay: sending to ' + target);
  items.getItem(target).sendCommand(streamUrl);
}
