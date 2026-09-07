package com.evoreview.context.acquisition;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface RepoSnapshot extends AutoCloseable {

    enum Kind {
        ARCHIVE_FULL,
        CONTENTS_LAZY
    }

    Kind kind();

    List<String> listFiles() throws IOException;

    Optional<String> readFile(String path) throws IOException;

    @Override
    default void close() {
    }
}
