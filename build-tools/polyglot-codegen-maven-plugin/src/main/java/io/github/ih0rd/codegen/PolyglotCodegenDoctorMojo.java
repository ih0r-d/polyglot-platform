package io.github.ih0rd.codegen;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Maven goal alias for check with more DX-friendly naming. */
@Mojo(name = "doctor", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PolyglotCodegenDoctorMojo extends AbstractPolyglotCodegenCheckLikeMojo {}
