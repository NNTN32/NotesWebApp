package com.example.notesWeb.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class FeatureStructureTest {
    private static final Path ROOT=Path.of("src/main/java/com/example/notesWeb");
    private static final String BASE="com.example.notesWeb.";
    private List<Path> sources() throws Exception {
        try (var files=Files.walk(ROOT)) {
            return files.filter(p->p.toString().endsWith(".java")).toList();
        }
    }
    private String packageName(String source) {
        var matcher=Pattern.compile("(?m)^package ([\\w.]+);").matcher(source);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    @Test void productionPackagesHaveExplicitFeatureOrPlatformOwnership() throws Exception {
        var allowed=Set.of("auth","notes","todos","platform","shared","bootstrap");
        for(var path:sources()) {
            String pkg=packageName(Files.readString(path));
            assertThat(path).endsWith(Path.of(pkg.replace('.','/'),path.getFileName().toString()));
            if(path.getFileName().toString().equals("NotesWebApplication.java")) continue;
            assertThat(allowed).as(path.toString()).contains(pkg.substring(BASE.length()).split("\\.")[0]);
        }
    }
    @Test void genericDeliveryInfrastructureDoesNotImportBusinessFeatures() throws Exception {
        for(var path:sources()) {
            String source=Files.readString(path),pkg=packageName(source);
            if(pkg.startsWith(BASE+"platform.messaging") || pkg.startsWith(BASE+"platform.redis") || pkg.startsWith(BASE+"shared"))
                assertThat(source).as(path.toString()).doesNotContain(BASE+"auth.",BASE+"notes.",BASE+"todos.");
        }
    }
    @Test void featuresDoNotCallAnotherFeaturesControllersServicesOrConsumers() throws Exception {
        for(var path:sources()) {
            String source=Files.readString(path),pkg=packageName(source);
            for(String feature:List.of("auth","notes","todos")) {
                if(!pkg.startsWith(BASE+feature+".")) continue;
                for(String peer:List.of("auth","notes","todos")) {
                    if(peer.equals(feature)) continue;
                    var forbidden=Pattern.compile(Pattern.quote(BASE+peer)+"(?:\\.\\w+)*\\.(?:api|application|messaging)\\.");
                    assertThat(forbidden.matcher(source).find()).as(path+" must not depend on "+peer+" behavior").isFalse();
                }
            }
        }
    }
}
