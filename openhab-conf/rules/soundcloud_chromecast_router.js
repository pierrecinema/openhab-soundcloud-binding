// SoundCloud Chromecast Router (automatic device lookup)
// Triggers: Item Command on SC_CC_Control, SC_CC_Stop, SC_CC_Volume, SC_CC_Mute, SC_CC_PlayURI
//
// SC_CCTarget holds the name of the selected Chromecast playuri item. The rule finds the
// Chromecast thing behind that item and forwards the command to the item linked to the
// matching channel (control / stop / volume / mute / playuri). No per-device mapping needed.
var CHANNELS = { SC_CC_Control: 'control', SC_CC_Stop: 'stop', SC_CC_Volume: 'volume', SC_CC_Mute: 'mute', SC_CC_PlayURI: 'playuri' };
var STATE_ITEM = 'SC_State'; // item linked to player#playback-state

var triggerItem = event.itemName;
var cmd = event.receivedCommand ? event.receivedCommand.toString() : '?';
var targetName = String(items.getItem('SC_CCTarget').state);

if (targetName && targetName !== 'NULL') {
  var reg = osgi.getService('org.openhab.core.thing.link.ItemChannelLinkRegistry');
  var ChannelUID = Java.type('org.openhab.core.thing.ChannelUID');
  var bound = reg.getBoundChannels(targetName).iterator();
  if (!bound.hasNext()) {
    logger.warn('SC Router: ' + targetName + ' is not linked to a Chromecast channel');
  } else {
    var thingUID = bound.next().getThingUID().toString();
    var linked = reg.getLinkedItemNames(new ChannelUID(thingUID + ':' + CHANNELS[triggerItem])).iterator();
    if (!linked.hasNext()) {
      logger.warn('SC Router: no item linked to ' + thingUID + ':' + CHANNELS[triggerItem]);
    } else {
      var destName = linked.next();
      items.getItem(destName).sendCommand(cmd);
      logger.info('SC Router: ' + triggerItem + ' -> ' + destName + ' (' + cmd.substring(0, 40) + ')');
    }
  }
  // Keep the binding's playback state (progress timer) in sync with pause / resume
  if (triggerItem === 'SC_CC_Control' && (cmd === 'PAUSE' || cmd === 'PLAY')) {
    items.getItem(STATE_ITEM).sendCommand(cmd);
  }
}
