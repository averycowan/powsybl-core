/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.network.immutable;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionProviders;
import com.powsybl.iidm.network.Identifiable;

import java.util.Collection;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * An abstract immutable {@link Identifiable}.
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
abstract class AbstractImmutableIdentifiable<I extends Identifiable<I>> implements Identifiable<I> {

    protected static ExtensionProviders<ImmutableWrapperExtension> providers;

    protected final I identifiable;

    protected final ImmutableCacheIndex cache;

    protected AbstractImmutableIdentifiable(I network) {
        this.identifiable = network;
        this.cache = new ImmutableCacheIndex((ImmutableNetwork) this);
    }

    protected AbstractImmutableIdentifiable(I identifiable, ImmutableCacheIndex cache) {
        this.identifiable = Objects.requireNonNull(identifiable);
        this.cache = cache;
    }

    @Override
    public String getId() {
        return identifiable.getId();
    }

    @Override
    public String getName() {
        return identifiable.getName();
    }

    @Override
    public boolean hasProperty() {
        return identifiable.hasProperty();
    }

    @Override
    public Properties getProperties() {
        return identifiable.getProperties();
    }

    /**
     * Mutative operation is not allowed. It will throw an exception in runtime.
     * @return
     */
    @Override
    public <E extends Extension<I>> void addExtension(Class<? super E> type, E extension) {
        throw ImmutableNetwork.createUnmodifiableNetworkException();
    }

    private static void lazyInitExtProviders() {
        providers = ExtensionProviders.createProvider(ImmutableWrapperExtension.class, AbstractImmutableWrapperExtension.IMMU_WRAPPER_EXT_CATE_NAME);
    }

    @Override
    public <E extends Extension<I>> E getExtension(Class<? super E> type) {
        return getExtensionByName(identifiable.getExtension(type).getName());
    }

    @Override
    public <E extends Extension<I>> E getExtensionByName(String name) {
        if (providers == null) {
            lazyInitExtProviders();
        }
        ImmutableWrapperExtension immutableWrapperExtension = providers.findProvider(name);
        if (immutableWrapperExtension != null) {
            return (E) immutableWrapperExtension.wrap(identifiable.getExtensionByName(name), this);
        } else {
            return identifiable.getExtensionByName(name);
        }
    }

    /**
     * Mutative operation is not allowed. It will throw an exception in runtime.
     * @return
     */
    @Override
    public <E extends Extension<I>> boolean removeExtension(Class<E> type) {
        throw ImmutableNetwork.createUnmodifiableNetworkException();
    }

    @Override
    public <E extends Extension<I>> Collection<E> getExtensions() {
        return identifiable.getExtensions().stream()
                .map(Extension::getName)
                .map(this::getExtensionByName)
                .map(e -> (E) e)
                .collect(Collectors.toList());
    }
}
