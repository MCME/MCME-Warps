package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.core.WarpIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.mcmiddleearth.warps.velocity.warps.TestWarps.BOB;
import static com.mcmiddleearth.warps.velocity.warps.TestWarps.publicWarp;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every change to a warp is made on a copy that then replaces the original ({@link WarpStore#update}).
 * So a field the copy constructor forgets is a field every edit silently resets.
 */
class WarpCopyTest {

    @TempDir Path warpsDir;

    private static Warp warpWithEveryFieldSet() {
        Warp warp = publicWarp("Rivendell");
        warp.setIcon(WarpIcon.WIP);
        warp.setLayer("major");
        warp.setWelcomeMessage("<gold>Welcome");
        warp.setVisits(42);
        warp.addMember(BOB);
        return warp;
    }

    @Test
    void theCopyConstructorCopiesEveryField() throws Exception {
        Warp original = warpWithEveryFieldSet();
        Warp copy = new Warp(original);

        List<String> different = new ArrayList<>();
        for (Class<?> type = Warp.class; type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (!java.util.Objects.equals(field.get(original), field.get(copy))) {
                    different.add(field.getName());
                }
            }
        }
        assertEquals(List.of(), different, "fields the copy does not carry over");
    }

    @Test
    void renamingAWarpKeepsItsIconAndLayer() throws Exception {
        WarpStore store = TestWarps.store(warpsDir);
        store.add(warpWithEveryFieldSet());

        store.update(store.getPublic("Rivendell"), w -> w.setName("Imladris"));

        Warp renamed = store.getPublic("Imladris");
        assertEquals(WarpIcon.WIP, renamed.getIcon());
        assertEquals("major", renamed.getLayer());
    }

    @Test
    void settingTheIconKeepsTheLayerAndTheOtherWayRound() throws Exception {
        WarpStore store = TestWarps.store(warpsDir);
        store.add(warpWithEveryFieldSet());

        store.update(store.getPublic("Rivendell"), w -> w.setIcon(WarpIcon.PIRATE));
        assertEquals("major", store.getPublic("Rivendell").getLayer(), "setIcon must not reset the layer");

        store.update(store.getPublic("Rivendell"), w -> w.setLayer("minor"));
        assertEquals(WarpIcon.PIRATE, store.getPublic("Rivendell").getIcon(), "setLayer must not reset the icon");
    }

    @Test
    void recordingAPlayersNewNameKeepsTheIconAndLayerOfTheirWarps() throws Exception {
        WarpStore store = TestWarps.store(warpsDir);
        store.add(warpWithEveryFieldSet()); // created by "Carol"

        store.renameCreator(TestWarps.CAROL, "Caroline", (w, e) -> { throw new AssertionError(e); });

        Warp warp = store.getPublic("Rivendell");
        assertEquals("Caroline", warp.getCreatorName());
        assertEquals(WarpIcon.WIP, warp.getIcon());
        assertEquals("major", warp.getLayer());
    }
}
