package com.ichi2.anki.lint;

import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;
import com.ichi2.anki.lint.rules.ConstantFieldDetector;
import com.ichi2.anki.lint.rules.DirectCalendarInstanceUsage;
import com.ichi2.anki.lint.rules.DirectSystemTimeInstantiation;
import com.ichi2.anki.lint.rules.DirectSystemCurrentTimeMillisUsage;
import com.ichi2.anki.lint.rules.DirectDateInstantiation;
import com.ichi2.anki.lint.rules.DirectGregorianInstantiation;
import com.ichi2.anki.lint.rules.DirectToastMakeTextUsage;
import com.ichi2.anki.lint.rules.DuplicateCrowdInStrings;
import com.ichi2.anki.lint.rules.DuplicateTextInPreferencesXml;
import com.ichi2.anki.lint.rules.InconsistentAnnotationUsage;
import com.ichi2.anki.lint.rules.NonPublicNonStaticFieldDetector;
import com.ichi2.anki.lint.rules.PrintStackTraceUsage;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IssueRegistry extends com.android.tools.lint.client.api.IssueRegistry {
    @NotNull
    @Override
    public List<Issue> getIssues() {
        // Keep this list lexicographically ordered.
        List<Issue> issues = new ArrayList<>();
        issues.add(DirectCalendarInstanceUsage.ISSUE);
        issues.add(DirectDateInstantiation.ISSUE);
        issues.add(DirectGregorianInstantiation.ISSUE);
        issues.add(DirectSystemCurrentTimeMillisUsage.ISSUE);
        issues.add(DirectSystemTimeInstantiation.ISSUE);
        issues.add(DirectToastMakeTextUsage.ISSUE);
        issues.add(DuplicateCrowdInStrings.ISSUE);
        issues.add(DuplicateTextInPreferencesXml.ISSUE);
        issues.add(InconsistentAnnotationUsage.ISSUE);
        issues.add(PrintStackTraceUsage.ISSUE);
        issues.add(NonPublicNonStaticFieldDetector.ISSUE);
        issues.add(ConstantFieldDetector.ISSUE);
        return issues;
    }

    @Override
    public int getApi() {
        return ApiKt.CURRENT_API;
    }
}