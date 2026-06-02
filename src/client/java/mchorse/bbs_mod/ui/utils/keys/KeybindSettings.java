package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeybindSettings
{
    private static final List<Class> classes = new ArrayList<>();
    private static final Map<String, Icon> CATEGORY_ICONS = new HashMap<>();

    static
    {
        CATEGORY_ICONS.put("all", Icons.KEY_CAP);
        CATEGORY_ICONS.put("camera", Icons.CAMERA);
        CATEGORY_ICONS.put("flight", Icons.PLANE);
        CATEGORY_ICONS.put("dashboard", Icons.LAYOUT);
        CATEGORY_ICONS.put("forms", Icons.MORPH);
        CATEGORY_ICONS.put("pixels", Icons.BRUSH);
        CATEGORY_ICONS.put("keyframes", Icons.CURVES);
        CATEGORY_ICONS.put("world", Icons.GLOBE);
        CATEGORY_ICONS.put("transformations", Icons.SCALE);
        CATEGORY_ICONS.put("film_controller", Icons.FILM);
        CATEGORY_ICONS.put("replays_editor", Icons.PLAYER);
        CATEGORY_ICONS.put("recording_groups", Icons.STOPWATCH);
        CATEGORY_ICONS.put("model_blocks", Icons.BLOCK);
        CATEGORY_ICONS.put("texture_picker", Icons.GALLERY);
    }

    public static void registerClasses()
    {
        classes.add(Keys.class);
    }

    public static void register(SettingsBuilder builder)
    {
        Map<String, List<KeyCombo>> combos = new HashMap<>();

        for (Class clazz : classes)
        {
            readKeyCombos(combos, clazz);
        }

        List<String> keys = new ArrayList<>(combos.keySet());

        keys.sort(Comparator.comparing((a) -> a));

        for (String key : keys)
        {
            List<KeyCombo> comboList = combos.get(key);

            builder.category(key, CATEGORY_ICONS.getOrDefault(key, Icons.KEY_CAP));

            for (KeyCombo combo : comboList)
            {
                builder.register(new ValueKeyCombo(combo.id, combo));
            }
        }
    }

    private static void readKeyCombos(Map<String, List<KeyCombo>> combos, Class clazz)
    {
        for (Field field : clazz.getDeclaredFields())
        {
            if (field.getType() != KeyCombo.class)
            {
                continue;
            }

            try
            {
                KeyCombo combo = (KeyCombo) field.get(null);
                List<KeyCombo> comboList = combos.computeIfAbsent(combo.categoryKey, (k) -> new ArrayList<>());

                comboList.add(combo);
            }
            catch (Exception e)
            {}
        }
    }
}