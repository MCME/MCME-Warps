package com.mcmiddleearth.warps.velocity.warps;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The disk side of {@link WarpStore}, isolated behind an interface so the store's crash-safety
 * ordering (write-before-swap, delete-before-forget) can be unit tested with an in-memory fake
 * that injects write/delete failures - without a running proxy or real files.
 */
public interface WarpFileIo {

    /** Writes (creating parent dirs) the warp's YAML to {@code path}, overwriting any existing file. */
    void write(Path path, Warp warp) throws IOException;

    /** Deletes the file if present; returns whether a file was actually deleted. */
    boolean deleteIfExists(Path path) throws IOException;

    /** Whether a file exists at {@code path}. */
    boolean exists(Path path);
}
