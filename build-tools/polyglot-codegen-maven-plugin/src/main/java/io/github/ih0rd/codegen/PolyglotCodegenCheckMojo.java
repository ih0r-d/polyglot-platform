package io.github.ih0rd.codegen;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Maven goal that checks generated contracts for drift without writing files. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PolyglotCodegenCheckMojo extends AbstractPolyglotCodegenCheckLikeMojo {}
