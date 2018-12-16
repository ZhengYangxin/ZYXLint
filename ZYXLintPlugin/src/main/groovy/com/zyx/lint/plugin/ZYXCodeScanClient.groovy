package com.zyx.lint.plugin

import com.android.build.gradle.internal.LintGradleClient
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.sdklib.BuildToolInfo
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.Project
/**
 * desc :
 * time  : 29/11/18.
 * author : pielan
 */

class ZYXCodeScanClient extends LintGradleClient {

    private org.gradle.api.Project gradleProject
    private AndroidProject modelProject

    ZYXCodeScanClient(IssueRegistry registry, LintCliFlags flags, org.gradle.api.Project gradleProject, AndroidProject modelProject, File sdkHome, Variant variant, BuildToolInfo buildToolInfo) {
        super(registry, flags, gradleProject, modelProject, sdkHome, variant, buildToolInfo)
        this.gradleProject = gradleProject
        this.modelProject = modelProject
    }

    @Override
    protected LintRequest createLintRequest(List<File> files) {
        System.out.println("==== files ====")
        LintRequest lintRequest = super.createLintRequest(files)
        addChangeFile(lintRequest)
        return lintRequest
    }

    private List<String> getPostCommitChange() {
        ArrayList<String> filterList = new ArrayList<String>()

        try {
            String projectDir = modelProject.getBuildFolder().getParentFile().getAbsolutePath()
            String command = "git diff --name-only --diff-filter=ACMRTUXB HEAD~0 $projectDir"
            String changeInfo = command.execute(null, gradleProject.getRootDir()).text.trim()
            if (changeInfo == null || changeInfo.empty) {
                return filterList
            }
            System.out.println("==== change file list ====")
            System.out.println("project : " + modelProject.name)
            System.out.println(changeInfo)
            String[] lines = changeInfo.split("\\n")
            return lines.toList()
        } catch (Exception ignored) {
            return filterList
        }
    }

    private addChangeFile(LintRequest lintRequest) {
        List<String> commitChanges = getPostCommitChange()
        for (String commitChange : commitChanges) {
            for (Project project : lintRequest.getProjects()) {
                project.addFile(new File(commitChange))//加入要扫描的文件
            }
        }
    }
}