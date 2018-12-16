package com.zyx.lint.plugin

import com.android.build.gradle.*
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.tasks.Lint
import org.gradle.api.*
import org.gradle.api.tasks.TaskState
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import javax.inject.Inject
/**
 * desc :
 * time  : 16/12/18.
 * author : pielan
 */

class ZYXLintPlugin extends TestPlugin {
    ZYXLintExtension zyxLintExtension
    static String CONFIG_LINT_PATH = "/config/lint.xml"
    static String CONFIG_GIT_COMMIT_PATH = "/config/pre-commit"

    @Inject
    ZYXLintPlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        super(instantiator, registry);
    }

    @Override
    void apply(Project project) {
        zyxLintExtension = project.extensions.create("ZYXLint", ZYXLintExtension)
        applyTask(project, getAndroidVariants(project))
        createTask(project)
        createGitHooksTask(project)
    }

    private static final String PLUGIN_MISS_CONFIGURED_ERROR_MESSAGE = "Plugin requires the 'android' or 'android-library' plugin to be configured."

    /**
     * 获取project 项目 中 android项目 或者library项目 的 variant 列表
     * @param project 要编译的项目
     * @return variants列表
     */
    private static DomainObjectCollection<BaseVariant> getAndroidVariants(Project project) {

        if (project.getPlugins().hasPlugin(AppPlugin)) {

            return project.getPlugins().getPlugin(AppPlugin).extension.applicationVariants
        }

        if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            return project.getPlugins().getPlugin(LibraryPlugin).extension.libraryVariants
        }

        throw new ProjectConfigurationException(PLUGIN_MISS_CONFIGURED_ERROR_MESSAGE, null)
    }

    /**
     *  插件的实际应用：统一管理lint.xml和lintOptions，自动添加aar。
     * @param project 项目
     * @param variants 项目的variants
     */
    private void applyTask(Project project, DomainObjectCollection<BaseVariant> variants) {
        //去除gradle缓存的配置
        project.configurations.all {
            resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
            resolutionStrategy.cacheDynamicVersionsFor 0, 'seconds'
        }

        def lintTaskExists = false

        variants.all { variant ->
            //获取Lint Task
            def variantName = variant.name.capitalize()
            Lint lintTask = project.tasks.getByName("lint" + variantName) as Lint

            //Lint 会把project下的lint.xml和lintConfig指定的lint.xml进行合并，为了确保只执行插件中的规则，采取此策略
            File lintFile = project.file("lint.xml")

            //==========================  统一  lintOptions  开始=============================================//

            def newLintOptions = new LintOptions()

            //配置lintConfig的配置文件路径
            newLintOptions.lintConfig = lintFile

            //是否将所有的warnings视为errors
//            newLintOptions.warningsAsErrors = true

            //是否lint检测出错则停止编译
            newLintOptions.abortOnError = false

            //htmlReport打开
            newLintOptions.htmlReport = true
            newLintOptions.htmlOutput = project.file("${project.buildDir}/reports/lint/lint-result.html")

            //xmlReport打开 因为Jenkins上的插件需要xml文件
            newLintOptions.xmlReport = true
            newLintOptions.xmlOutput = project.file("${project.buildDir}/reports/lint/lint-result.xml")

            //配置 lint任务的配置为  newLintOptions
            lintTask.lintOptions = newLintOptions
            //==========================  统一  lintOptions  结束=============================================//

            //==========================  统一  lint.xml  开始=============================================//
            //lint任务执行前，先复制lint.xml
            lintTask.doFirst {
                //如果 lint.xml 存在，则改名为 lintOld.xml
                File lintOldFile = null
                if (lintFile.exists()) {
                    lintOldFile = project.file("lintOld.xml")
                    lintFile.renameTo(lintOldFile)
                }

                //进行 将plugin内置的lint.xml文件和项目下面的lint.xml进行复制合并操作
                def isLintXmlReady = copyLintXml(project, lintFile)
                //合并完毕后，将lintOld.xml 文件改名为 lint.xml
                if (!isLintXmlReady) {
                    if (lintOldFile != null) {
                        lintOldFile.renameTo(lintFile)
                    }
                    throw new GradleException("lint.xml不存在")
                }


            }

            //lint任务执行后，删除lint.xml
            project.gradle.taskGraph.afterTask { task, TaskState state ->
                if (task == lintTask) {
                    lintFile.delete()
                    if (lintOldFile != null) {
                        lintOldFile.renameTo(lintFile)
                    }
                }
            }
            //==========================  统一  lint.xml  结束=============================================//


            //==========================  在终端 执行命令 gradlew LintForZYX  的配置  开始=============================================//
            // 在终端 执行命令 gradlew LintForZYX 的时候，则会应用  lintTask
            if (!lintTaskExists) {
                lintTaskExists = true
                //创建一个task 名为  LintForZYX

//                project.getTasks().create(LINTFORZYX, ZYXCodeScanTask.class)
//                project.task(LINTFORZYX).dependsOn lintTask
            }
            //==========================  在终端 执行命令 gradlew LintForZYX  的配置  结束=============================================//
        }
    }

    void createTask(Project project) {
        def baseExtension = project.extensions.getByName("android")
        if (baseExtension instanceof AppExtension) {
            AppExtension extension = (AppExtension) baseExtension
            DomainObjectSet<ApplicationVariant> variants = extension.getApplicationVariants()
            variants.all {
                System.out.println(project.getName() + "==== application ====")
                if (it instanceof ApplicationVariantImpl) {
                    ApplicationVariantImpl variantImpl = (ApplicationVariantImpl) it
                    def globalScope = variantImpl.variantData.scope
                    project.getTasks().create(globalScope.getTaskName(ZYXCodeScanTask.NAME), ZYXCodeScanTask.class, new ZYXCodeScanTask.VitalConfigAction(globalScope, getProject()))
                }
            }
        } else if (baseExtension instanceof LibraryExtension) {
            // 说明这个是library
            System.out.println(project.getName() + "==== Library ====")
            LibraryExtension extension = (LibraryExtension) baseExtension
            DomainObjectSet<LibraryVariant> variants = extension.getLibraryVariants()
            variants.all {
                if (it instanceof LibraryVariantImpl) {
                    LibraryVariantImpl variantImpl = (LibraryVariantImpl) it
                    def globalScope = getVariantDataByLibrary(variantImpl)
                    project.getTasks().create(globalScope.scope.getTaskName(ZYXCodeScanTask.NAME), ZYXCodeScanTask.class, new ZYXCodeScanTask.VitalConfigAction(globalScope.scope, getProject()))
                }
            }
        }
    }


    /**
     * 复制 lint.xml 到 targetFile
     * @param project 项目
     * @param targetFile 复制到的目标文件
     * @return 是否复制成功
     */
    boolean copyLintXml(Project project, File targetFile) {
        //创建目录
        targetFile.parentFile.mkdirs()

        //目标文件为  resources/config/lint.xml文件
        InputStream lintIns = this.class.getResourceAsStream(CONFIG_LINT_PATH)
        OutputStream outputStream = new FileOutputStream(targetFile)

        // 未使用 或 使用了不支持try with resource的版本
        FileUtils.copy(lintIns, outputStream)
        FileUtils.closeQuietly(outputStream)
        FileUtils.closeQuietly(lintIns)

        //如果复制操作完成后，目标文件存在
        if (targetFile.exists()) {
            return true
        }
        return false
    }

    private void createGitHooksTask(Project project) {
        def preBuild = project.tasks.findByName("preBuild")

        if (preBuild == null) {
            throw new GradleException("lint  need depend on preBuild and clean task")
            return
        }

        def installGitHooks = project.getTasks().create("installGitHooks") .doLast {
            def PATH_POST_COMMIT = ".git/hooks/pre-commit"
            File postCommitFile = new File(project.rootProject.rootDir, PATH_POST_COMMIT)
            if (zyxLintExtension.checkCommit) {
                FileUtils.copyResourceFile(CONFIG_GIT_COMMIT_PATH, postCommitFile)
            } else {
                if (postCommitFile.exists()) {
                    postCommitFile.delete()
                }
            }
        }

        preBuild.finalizedBy installGitHooks
    }

    /**
     * 统一  自动添加AAR
     * @param project
     */
    private void addLintArr(Project project) {
        //配置project的dependencies配置，默认都自动加上 自定义lint检测的AAR包
        project.dependencies {
            //如果是android application项目
            if (project.getPlugins().hasPlugin('com.android.application')) {
                compile('com.xtc.lint:lint-check:+') {
                    force = true
                }
            } else {
                provided('com.xtc.lint:lint-check:+') {
                    force = true
                }
            }
        }

    }
}
