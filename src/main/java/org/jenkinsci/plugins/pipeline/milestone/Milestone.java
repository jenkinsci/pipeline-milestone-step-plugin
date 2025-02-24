/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Set;
import java.util.TreeSet;

/**
 * @deprecated No longer used. Only kept for backward compatibility.
 */
@Deprecated
class Milestone {
    /**
     * Milestone ordinal.
     */
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "Serial compatibility")
    final Integer ordinal;

    /**
     * Numbers of builds that passed this milestone but haven't passed the next one.
     */
    final Set<Integer> inSight = new TreeSet<>();

    /**
     * Last build that passed through the milestone, or null if none passed yet.
     */
    @CheckForNull
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "Serial compatibility")
    Integer lastBuild;

    Milestone(Integer ordinal) {
        throw new AssertionError("Milestone class is deprecated and should not be used.");
    }

}
