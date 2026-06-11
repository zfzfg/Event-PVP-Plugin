package de.zfzfg.eventplugin.integration;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class PluginDisplayBridge implements ExternalDisplayBridge {
    private final EventPlugin plugin;
    private final String pluginName;
    private final String[] pvpMethodNames;
    private final String[] eventMethodNames;
    private final Object target;
    private final boolean active;
    private final Set<String> loggedWarnings = new HashSet<>();
    private final Map<String, Method> methodCache = new HashMap<>();

    public PluginDisplayBridge(EventPlugin plugin, String pluginName, String[] pvpMethodNames, String[] eventMethodNames) {
        this.plugin = plugin;
        this.pluginName = pluginName;
        this.pvpMethodNames = pvpMethodNames;
        this.eventMethodNames = eventMethodNames;

        Plugin bukkitPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (bukkitPlugin != null && bukkitPlugin.isEnabled()) {
            Object apiTarget = resolveApiTarget(bukkitPlugin);
            this.target = apiTarget != null ? apiTarget : bukkitPlugin;
            this.active = this.target != null;
        } else {
            this.target = null;
            this.active = false;
        }

        if (!this.active) {
            plugin.getLogger().warning("Unable to initialize external display bridge for " + pluginName + ".");
        }
    }

    @Override
    public void markDirty() {
        // The controller handles dirty state in the plugin itself.
    }

    @Override
    public void refreshPvpBoard() {
        invokeFirstAvailable(pvpMethodNames, "PvP board");
    }

    @Override
    public void refreshEventBoard() {
        invokeFirstAvailable(eventMethodNames, "Event board");
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private Object resolveApiTarget(Plugin bukkitPlugin) {
        Object targetCandidate = invokeNoArgAccessor(bukkitPlugin, "getAPI");
        if (targetCandidate == null) {
            targetCandidate = invokeNoArgAccessor(bukkitPlugin, "getApi");
        }
        return targetCandidate != null ? targetCandidate : bukkitPlugin;
    }

    private Object invokeNoArgAccessor(Object targetObject, String methodName) {
        try {
            Method method = targetObject.getClass().getMethod(methodName);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(targetObject);
            }
        } catch (Exception ignored) {
            // ignored on purpose, this is a best-effort integration
        }
        return null;
    }

    private void invokeFirstAvailable(String[] methodNames, String description) {
        if (!active || target == null) {
            return;
        }

        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName);
            if (method != null) {
                try {
                    method.setAccessible(true);
                    method.invoke(target);
                    return;
                } catch (Exception e) {
                    getLogger().warning("Error invoking method '" + methodName + "' on " + pluginName + ": " + e.getMessage());
                    return;
                }
            }
        }

        String key = pluginName + ":" + description;
        if (!loggedWarnings.contains(key)) {
            getLogger().warning("Could not find any supported method for " + description + " on " + pluginName + " (checked: " + String.join(", ", methodNames) + ").");
            loggedWarnings.add(key);
        }
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        if (methodCache.containsKey(methodName)) {
            return methodCache.get(methodName);
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                        methodCache.put(methodName, method);
                        return method;
                    }
                }
            } catch (Exception ignored) {
                // continue walking class hierarchy
            }
            current = current.getSuperclass();
        }

        methodCache.put(methodName, null);
        return null;
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }
}
