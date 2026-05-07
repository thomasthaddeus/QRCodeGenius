plugins {
    id("com.android.application") version "9.2.0" apply false
}

buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                "io.netty:netty-codec-http2:4.1.132.Final",
                "io.netty:netty-codec-http:4.1.132.Final",
                "io.netty:netty-codec:4.1.125.Final",
                "io.netty:netty-common:4.1.118.Final",
                "io.netty:netty-handler:4.1.118.Final",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.jdom:jdom2:2.0.6.1",
                "org.apache.httpcomponents:httpclient:4.5.14",
            )
        }
    }
}
