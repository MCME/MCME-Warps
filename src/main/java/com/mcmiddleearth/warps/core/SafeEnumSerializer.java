package com.mcmiddleearth.warps.core;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public class SafeEnumSerializer<E extends Enum<E>> implements TypeSerializer<E> {

    private final Class<E> enumClass;
    private final E defaultValue;

    public SafeEnumSerializer(Class<E> enumClass, E defaultValue) {
        this.enumClass = enumClass;
        this.defaultValue = defaultValue;
    }

    @Override
    public E deserialize(Type type, ConfigurationNode node) {
        String raw = node.getString();
        if (raw == null) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    @Override
    public void serialize(Type type, E obj, ConfigurationNode node) throws SerializationException {
        node.set(obj.name());
    }
}