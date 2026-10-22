// SoundCloud Auto-Play
// NOTE: Only needed for binding v1.5.5 and older. Since v1.5.6 the binding sends new tracks itself — disable this rule.
// Trigger: Item State Change on SC_StreamURL (item linked to player#stream-url)
//
// Fires whenever a new track is loaded (search result, next / previous, playlist, repeat)
// and sends the new stream URL via SC_CC_PlayURI to the router, which forwards it to the
// selected Chromecast and retries once if the Chromecast first had to launch its cast app.
// Pause / resume do not change the URL, so they never restart the track.
var streamUrl = String(items.getItem('SC_StreamURL').state);
var target = String(items.getItem('SC_CCTarget').state);

if (streamUrl.indexOf('http') !== 0) {
  console.debug('SoundCloud AutoPlay: no stream URL, aborted');
} else if (!target || target === 'NULL' || target === '') {
  console.debug('SoundCloud AutoPlay: no Chromecast target, aborted');
} else {
  console.info('SoundCloud AutoPlay: sending to ' + target + ' via SC_CC_PlayURI');
  items.getItem('SC_CC_PlayURI').sendCommand(streamUrl);
}
