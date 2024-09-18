package org.hisp.dhis.setting;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonPrimitive;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.lang.Character.toUpperCase;

/**
 * {@link SystemSettings} or {@link UserSettings} represented by a set of keys and their values.
 *
 * <p>This only contain those settings that currently do have a defined value in the DB data.
 *
 * <p>Initially values are provided as raw {@link String} values.
 *
 * <p>When values are accessed as a specific type the conversion is applied to the {@link
 * #rawValues} and remembered as {@link #typedValues}.
 *
 * @author Jan Bernitt
 * @since 2.42
 */
@Slf4j
@ToString
@EqualsAndHashCode
final class LazySettings implements SystemSettings, UserSettings {

  private static final Map<String, Serializable> DEFAULTS = extractDefaults();

  static LazySettings of(Class<? extends Settings> type, @Nonnull Settings base, @Nonnull Map<String, String> settings) {
    Map<String, String> merged = new HashMap<>(base.toMap());
    merged.putAll(settings);
    return of (type, merged);
  }

  @Nonnull
  static LazySettings of(Class<? extends Settings> type, @Nonnull Map<String, String> settings) {
    return of(type, settings, UnaryOperator.identity());
  }
  @Nonnull
  static LazySettings of(Class<? extends Settings> type, @Nonnull Map<String, String> settings, @Nonnull UnaryOperator<String> decoder) {
    // sorts alphabetically which is essential for the binary search lookup used
    TreeMap<String, String> from =
        settings instanceof TreeMap<String, String> tm ? tm : new TreeMap<>(settings);
    String[] keys = new String[from.size()];
    String[] values = new String[keys.length];
    int i = 0;
    for (Map.Entry<String, String> e : from.entrySet()) {
      keys[i] = e.getKey();
      values[i++] = decoder.apply(e.getValue());
    }
    return new LazySettings(type, keys, values);
  }

  private final Class<? extends Settings> type;
  private final String[] keys;
  private final String[] rawValues;
  @EqualsAndHashCode.Exclude
  private final Serializable[] typedValues;

  private LazySettings(Class<? extends Settings> type, String[] keys, String[] values) {
    this.type = type;
    this.keys = keys;
    this.rawValues = values;
    this.typedValues = new Serializable[values.length];
  }

  @Override
  public Set<String> keys() {
    return Set.of(keys);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E extends Enum<E>> E asEnum(String key, @Nonnull E defaultValue) {
    Serializable value = orDefault(key, defaultValue);
    if (value != null && value.getClass() == defaultValue.getClass()) return (E) value;
    return (E) parsed(key, defaultValue, raw -> Enum.valueOf(defaultValue.getClass(), raw));
  }

  @Nonnull
  @Override
  public String asString(String key, @Nonnull String defaultValue) {
    if (orDefault(key, defaultValue) instanceof String value) return value;
    // Note: index will exist as otherwise we would have exited above
    String rawValue = rawValues[indexOf(key)];
    return rawValue == null ? "" : rawValue;
  }

  @Nonnull
  @Override
  public Date asDate(String key, @Nonnull Date defaultValue) {
    if (orDefault(key, defaultValue) instanceof Date value) return value;
    return parsed(key, defaultValue, LazySettings::parseDate);
  }

  @Nonnull
  @Override
  public Locale asLocale(String key, @Nonnull Locale defaultValue) {
    if (orDefault(key, defaultValue) instanceof Locale value) return value;
    return parsed(key, defaultValue, LocaleUtils::toLocale);
  }

  @Override
  public int asInt(String key, int defaultValue) {
    if (orDefault(key, defaultValue) instanceof Integer value) return value;
    return parsed(key, defaultValue, Integer::valueOf);
  }

  @Override
  public double asDouble(String key, double defaultValue) {
    if (orDefault(key, defaultValue) instanceof Double value) return value;
    return parsed(key, defaultValue, Double::valueOf);
  }

  @Override
  public boolean asBoolean(String key, boolean defaultValue) {
    if (orDefault(key, defaultValue) instanceof Boolean value) return value;
    return parsed(key, defaultValue, Boolean::valueOf);
  }

  @Override
  public Map<String, String> toMap() {
    Map<String, String> map = new TreeMap<>();
    for (int i = 0; i < keys.length; i++) {
      map.put(keys[i], rawValues[i]);
    }
    return map;
  }

  @Override
  public JsonMap<? extends JsonPrimitive> toJson() {
    //TODO
    return null;
  }

  private int indexOf(String key) {
    int i = Arrays.binarySearch(keys, key);
    if (i >= 0 || key.startsWith("key")) return i;
    // try with key prefix
    key = "key" + toUpperCase(key.charAt(0)) + key.substring(1);
    return Arrays.binarySearch(keys, key);
  }

  private Serializable orDefault(String key, Serializable defaultValue) {
    int i = indexOf(key);
    return i < 0 ? defaultValue : typedValues[i];
  }

  @Nonnull
  private <T extends Serializable> T parsed(String key, @Nonnull T defaultValue, Function<String, T> parse) {
    int i = indexOf(key);
    String raw = rawValues[i];
    if (raw == null || raw.isEmpty()) {
      typedValues[i] = defaultValue; // remember the default
      return defaultValue;
    }
    T res = defaultValue;
    try {
      res = parse.apply(raw);
    } catch (Exception ex) {
      log.warn(
          "Setting {} has a raw value that cannot be parsed successfully as a {}; using default {}: {}",
          key, defaultValue.getClass().getSimpleName(), defaultValue, raw);
      // fall-through and use the default value
    }
    typedValues[i] = res;
    return res;
  }

  private static Date parseDate(String raw) {
    if (raw.isEmpty()) return new Date(0);
    if (raw.matches("^[0-9]+$")) return new Date(Long.parseLong(raw));
    return Date.from(LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant());
  }

  private static Map<String, Serializable> extractDefaults() {
    Map<String, Serializable> defaults = new HashMap<>();
    Proxy.newProxyInstance(
        ClassLoader.getSystemClassLoader(),
        new Class[] {SystemSettings.class},
        (proxy, method, args) -> {
          if (method.isDefault()) return getDefaultMethodHandle(method).bindTo(proxy).invokeWithArguments( args );
          return null;
        });
    return Map.copyOf(defaults);
  }

  private static MethodHandle getDefaultMethodHandle(Method method ) {
    try {
      Class<?> declaringClass = method.getDeclaringClass();
      return MethodHandles.lookup()
          .findSpecial( declaringClass, method.getName(),
              MethodType.methodType( method.getReturnType(), method.getParameterTypes() ),
              declaringClass );
    } catch ( Exception ex ) {
      throw new RuntimeException(ex);
    }
  }
}
