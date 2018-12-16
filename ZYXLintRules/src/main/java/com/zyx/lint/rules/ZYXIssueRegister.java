package com.zyx.lint.rules;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.zyx.lint.rules.detectors.java.NewThreadDetector;
import com.zyx.lint.rules.detectors.java.ZYXViewHolderItemNameDetector;
import com.zyx.lint.rules.detectors.xml.ZYXViewIdNameDetector;

import java.util.Arrays;
import java.util.List;

/**
 * desc :
 * time  : 15/12/18.
 * author : pielan
 */

public class ZYXIssueRegister extends IssueRegistry{
    @Override
    public synchronized List<Issue> getIssues() {
        System.out.println("***************************************************");
        System.out.println("**************** lint 开始静态分析代码 *****************");
        System.out.println("***************************************************");
        return Arrays.asList(ZYXViewIdNameDetector.ISSUE,
                ZYXViewHolderItemNameDetector.ISSUE,
                NewThreadDetector.ISSUE);
    }
}
