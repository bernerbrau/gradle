/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.binarycompatibility.rules;

import japicmp.model.JApiClass;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiCompatibilityChange;
import japicmp.model.JApiHasAnnotations;
import japicmp.model.JApiImplementedInterface;
import me.champeau.gradle.japicmp.report.Violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BinaryBreakingChangesRule extends AbstractGradleViolationRule {

    private static final List<JApiCompatibilityChange> IGNORED_CHANGE_TYPES = new ArrayList<>();

    static {
        IGNORED_CHANGE_TYPES.add(JApiCompatibilityChange.METHOD_REMOVED_IN_SUPERCLASS); //the removal of the method will be reported
        IGNORED_CHANGE_TYPES.add(JApiCompatibilityChange.INTERFACE_REMOVED); //the removed methods will be reported
        IGNORED_CHANGE_TYPES.add(JApiCompatibilityChange.INTERFACE_ADDED); //the added methods will be reported
        IGNORED_CHANGE_TYPES.add(JApiCompatibilityChange.CONSTRUCTOR_REMOVED); //we do not consider constructors public API, otherwise we would also need to annotate them with @Incubating
        IGNORED_CHANGE_TYPES.add(JApiCompatibilityChange.CONSTRUCTOR_LESS_ACCESSIBLE);
    }

    public BinaryBreakingChangesRule(Map<String, String> acceptedApiChanges) {
        super(acceptedApiChanges);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Violation maybeViolation(final JApiCompatibility member) {
        if (!member.isBinaryCompatible()) {
            if ((member instanceof JApiClass) && (member.getCompatibilityChanges().isEmpty())) {
                // A member of the class breaks binary compatibility.
                // That will be handled when the member is passed to `maybeViolation`.
                return null;
            }
            if (member instanceof JApiImplementedInterface) {
                // The changes about the interface's methods will be reported already
                return null;
            }
            for (JApiCompatibilityChange change : member.getCompatibilityChanges()) {
                if (IGNORED_CHANGE_TYPES.contains(change)) {
                    return null;
                }
            }
            if (member instanceof JApiHasAnnotations) {
                if (isIncubating((JApiHasAnnotations) member)) {
                    return Violation.warning(member, "Changed public API (@Incubating)");
                }
            }
            return acceptOrReject(member, Violation.notBinaryCompatible(member));
        }
        return null;
    }

}
