package com.zyx.lint.plugin;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.internal.LintGradleClient;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.tasks.GroovyGradleDetector;
import com.android.build.gradle.tasks.Lint;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.Warning;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.utils.Pair;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * desc :
 * time  : 16/12/18.
 * author : pielan
 */

class ZYXCodeScanTask extends Lint {

    public static final String NAME = "ZYXCodeScanTask";

    @Nullable private LintOptions lintOptions;
    @Nullable private File sdkHome;
    private boolean fatalOnly = true;
    @Nullable private File reportsDir;
    private ToolingModelBuilderRegistry toolingRegistry;

    public ZYXCodeScanTask() {
    }

    @Override
    public void lint() throws IOException {
        AndroidProject modelProject = createAndroidProject(getProject());
        if (getVariantName() != null && !getVariantName().isEmpty()) {
            for (Variant variant : modelProject.getVariants()) {
                if (variant.getName().equals(getVariantName())) {
                    lintSingleVariant(modelProject, variant);
                }
            }
        } else {
            lintAllVariants(modelProject);
        }
    }

    public void setLintOptions(@NonNull LintOptions lintOptions) {
        this.lintOptions = lintOptions;
    }

    public void setSdkHome(@NonNull File sdkHome) {
        this.sdkHome = sdkHome;
    }


    public void setReportsDir(@Nullable File reportDir) {
        this.reportsDir = reportDir;
    }

    @Override
    public void setFatalOnly(boolean fatalOnly) {
        super.setFatalOnly(fatalOnly);
        this.fatalOnly = fatalOnly;
    }

    @Override
    public void setToolingRegistry(ToolingModelBuilderRegistry toolingRegistry) {
        this.toolingRegistry = toolingRegistry;
    }

    @Override
    public void lintSingleVariant(AndroidProject modelProject, Variant variant) {
        runLint(modelProject, variant, true);

    }

    private static BuiltinIssueRegistry createIssueRegistry() {
        return new LintGradleIssueRegistry();
    }

    /** Runs lint on the given variant and returns the set of warnings */
    private Pair<List<Warning>,LintBaseline> runLint(
            /*
             * Note that as soon as we disable {@link #MODEL_LIBRARIES} this is
             * unused and we can delete it and all the callers passing it recursively
             */
            @NonNull AndroidProject modelProject,
            @NonNull Variant variant,
            boolean report) {
        IssueRegistry registry = createIssueRegistry();
        LintCliFlags flags = new LintCliFlags();
        ZYXCodeScanClient client =
                new ZYXCodeScanClient(
                        registry,
                        flags,
                        getProject(),
                        modelProject,
                        sdkHome,
                        variant,
                        getBuildTools());
        if (fatalOnly) {
            if (lintOptions != null && !lintOptions.isCheckReleaseBuilds()) {
                return Pair.of(Collections.emptyList(), null);
            }
            flags.setFatalOnly(true);
        }
        if (lintOptions != null) {
            syncOptions(lintOptions, client, flags, variant, getProject(), reportsDir, report,
                    fatalOnly);
        }
        if (!report || fatalOnly) {
            flags.setQuiet(true);
        }
        flags.setWriteBaselineIfMissing(report);

        Pair<List<Warning>,LintBaseline> warnings;
        try {
            warnings = client.run(registry);
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e);
        }

        if (report && client.haveErrors() && flags.isSetExitCode()) {
            abort();
        }

        return warnings;
    }

    private static void syncOptions(
            @NonNull LintOptions options,
            @NonNull LintGradleClient client,
            @NonNull LintCliFlags flags,
            @Nullable Variant variant,
            @NonNull Project project,
            @Nullable File reportsDir,
            boolean report,
            boolean fatalOnly) {
        options.syncTo(client, flags, variant != null ? variant.getName() : null, project,
                reportsDir, report);

        boolean displayEmpty = !(fatalOnly || flags.isQuiet());
        for (Reporter reporter : flags.getReporters()) {
            reporter.setDisplayEmpty(displayEmpty);
        }
    }

    private void abort() {
        String message;
        if (fatalOnly) {
            message = "" +
                    "Lint found fatal errors while assembling a release target.\n" +
                    "\n" +
                    "To proceed, either fix the issues identified by lint, or modify your build script as follows:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        checkReleaseBuilds false\n" +
                    "        // Or, if you prefer, you can continue to check for errors in release builds,\n" +
                    "        // but continue the build even when errors are found:\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "...";
        } else {
            message = "" +
                    "Lint found errors in the project; aborting build.\n" +
                    "\n" +
                    "Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "...";
        }
        throw new GradleException(message);
    }


    private static class LintGradleIssueRegistry extends BuiltinIssueRegistry {
        private boolean mInitialized;

        public LintGradleIssueRegistry() {}

        @NonNull
        @Override
        public List<Issue> getIssues() {
            List<Issue> issues = super.getIssues();
            if (!mInitialized) {
                mInitialized = true;
                for (Issue issue : issues) {
                    if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                        issue.setImplementation(IMPLEMENTATION);
                    }
                }
            }

            return issues;
        }
    }

    static final Implementation IMPLEMENTATION = new Implementation(
            GroovyGradleDetector.class,
            Scope.GRADLE_SCOPE);


    private VariantScope getScope() {
        Project project = getProject();
        Object baseExtension = project.getExtensions().getByName("android");
        if (baseExtension instanceof AppExtension) {
            AppExtension extension = (AppExtension) baseExtension;
            DomainObjectSet<ApplicationVariant> variants = extension.getApplicationVariants();

            for (ApplicationVariant it : variants) {
                if (it instanceof ApplicationVariantImpl) {
                    ApplicationVariantImpl variantImpl = (ApplicationVariantImpl) it;
                    ApplicationVariantData applicationVariantData = (ApplicationVariantData) ReflectionExtUtil.getFieldValue(variantImpl, "variantData");
                    VariantScope globalScope = applicationVariantData.getScope();
                    if (globalScope != null) {
                        return globalScope;
                    }
                }
            }
        } else if (baseExtension instanceof LibraryExtension) {
            // 说明这个是library
            LibraryExtension extension = (LibraryExtension) baseExtension;
            DomainObjectSet<LibraryVariant> variants = extension.getLibraryVariants();

            for (LibraryVariant it : variants) {
                if (it instanceof LibraryVariantImpl) {
                    LibraryVariantImpl variantImpl = (LibraryVariantImpl) it;
                    LibraryVariantData baseVariantData = (LibraryVariantData) ReflectionExtUtil.getFieldValue(variantImpl, "variantData");
//                    def globalScope = getVariantDataByLibrary(variantImpl)
                    VariantScope globalScope = baseVariantData.getScope();
                    if (globalScope != null) {
                      return globalScope;
                  }
                }
            }

        }
        return null;
    }

    GlobalScope getGlobalScope() {
        return getScope().getGlobalScope();
    }

    public static class VitalConfigAction implements TaskConfigAction<ZYXCodeScanTask> {

        private final VariantScope scope;
        private final Project project;

        public VitalConfigAction(@NonNull VariantScope scope, Project project) {
            this.scope = scope;
            this.project = project;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(NAME);
        }

        @NonNull
        @Override
        public Class<ZYXCodeScanTask> getType() {
            return ZYXCodeScanTask.class;
        }

        @Override
        public void execute(@NonNull ZYXCodeScanTask task) {
            String variantName = scope.getVariantData().getVariantConfiguration().getFullName();
            GlobalScope globalScope = scope.getGlobalScope();
            task.setLintOptions(globalScope.getExtension().getLintOptions());
            task.setSdkHome(checkNotNull(
                    globalScope.getSdkHandler().getSdkFolder(), "SDK not set up."));
            task.setToolingRegistry(globalScope.getToolingRegistry());
            task.setReportsDir(globalScope.getReportsDir());
            task.setVariantName(variantName);
            task.setFatalOnly(false);
            task.setDescription(
                    "Runs lint on just the fatal issues in the " + variantName + " build.");
            task.setAndroidBuilder(globalScope.getAndroidBuilder());
            project.getTasks().add(task);
        }
    }

    private AndroidProject createAndroidProject(@NonNull Project gradleProject) {
        String modelName = AndroidProject.class.getName();
        ToolingModelBuilder modelBuilder = toolingRegistry.getBuilder(modelName);
        assert modelBuilder != null;
        return (AndroidProject) modelBuilder.buildAll(modelName, gradleProject);
    }

}
