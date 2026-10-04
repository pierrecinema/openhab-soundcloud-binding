package org.openhab.binding.soundcloud.internal;

import static org.openhab.binding.soundcloud.internal.SoundCloudBindingConstants.THING_TYPE_ACCOUNT;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.soundcloud.internal.handler.SoundCloudHandler;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;

@Component(service = ThingHandlerFactory.class)
@NonNullByDefault
public class SoundCloudHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT);

    private final StorageService storageService;
    private final HttpService httpService;

    @Activate
    public SoundCloudHandlerFactory(@Reference StorageService storageService,
            @Reference HttpService httpService) {
        this.storageService = storageService;
        this.httpService = httpService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_ACCOUNT.equals(thing.getThingTypeUID())) {
            return new SoundCloudHandler(thing, storageService, httpService);
        }
        return null;
    }
}
