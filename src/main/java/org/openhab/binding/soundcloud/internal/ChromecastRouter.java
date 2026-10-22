package org.openhab.binding.soundcloud.internal;

import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leitet Befehle an den gewählten Chromecast weiter (ersetzt die frühere Router-Regel).
 *
 * Das Ziel ist der Name des Play-URI-Items des Chromecasts (Channel chromecast-target). Über dessen
 * Channel-Link wird das Chromecast-Thing ermittelt; die übrigen Items (control, stop, volume, mute)
 * werden über die Links der gleichnamigen Channels dieses Things gefunden.
 */
@NonNullByDefault
public class ChromecastRouter {

    public static final String CC_PLAYURI = "playuri";
    public static final String CC_CONTROL = "control";
    public static final String CC_STOP = "stop";
    public static final String CC_VOLUME = "volume";
    public static final String CC_MUTE = "mute";

    private final Logger logger = LoggerFactory.getLogger(ChromecastRouter.class);
    private final ItemChannelLinkRegistry linkRegistry;
    private final ItemRegistry itemRegistry;
    private final EventPublisher eventPublisher;

    public ChromecastRouter(ItemChannelLinkRegistry linkRegistry, ItemRegistry itemRegistry,
            EventPublisher eventPublisher) {
        this.linkRegistry = linkRegistry;
        this.itemRegistry = itemRegistry;
        this.eventPublisher = eventPublisher;
    }

    /** Chromecast-Thing hinter dem Ziel-Item, oder null wenn das Item nicht verlinkt ist. */
    public @Nullable ThingUID resolveThing(@Nullable String targetItem) {
        if (targetItem == null || targetItem.isBlank()) {
            return null;
        }
        Iterator<ChannelUID> it = linkRegistry.getBoundChannels(targetItem).iterator();
        return it.hasNext() ? it.next().getThingUID() : null;
    }

    /** Item, das mit dem Channel {@code channelId} des Chromecasts verlinkt ist. */
    public @Nullable String resolveItem(@Nullable String targetItem, String channelId) {
        if (CC_PLAYURI.equals(channelId) && targetItem != null && !targetItem.isBlank()) {
            return targetItem;
        }
        ThingUID thingUID = resolveThing(targetItem);
        if (thingUID == null) {
            return null;
        }
        Set<String> names = linkRegistry.getLinkedItemNames(new ChannelUID(thingUID, channelId));
        return names.isEmpty() ? null : names.iterator().next();
    }

    /** Schickt einen Befehl an das passende Item des gewählten Chromecasts. */
    public boolean send(@Nullable String targetItem, String channelId, Command command) {
        String itemName = resolveItem(targetItem, channelId);
        if (itemName == null) {
            logger.debug("Kein Item für Chromecast-Channel '{}' (Ziel {})", channelId, targetItem);
            return false;
        }
        eventPublisher.post(ItemEventFactory.createCommandEvent(itemName, command));
        logger.debug("Chromecast: {} -> {} ({})", channelId, itemName, command);
        return true;
    }

    /** Aktueller Zustand des passenden Chromecast-Items, oder null. */
    public @Nullable State state(@Nullable String targetItem, String channelId) {
        String itemName = resolveItem(targetItem, channelId);
        if (itemName == null) {
            return null;
        }
        try {
            Item item = itemRegistry.getItem(itemName);
            return item.getState();
        } catch (ItemNotFoundException e) {
            return null;
        }
    }
}
