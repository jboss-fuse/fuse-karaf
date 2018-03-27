/**
 *  Copyright 2005-2018 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.eclipse.jgit.errors;

/**
 * Stops the driver loop of walker and finish with current results.
 *
 * @see org.eclipse.jgit.revwalk.filter.RevFilter
 */
public final class StopWalkException extends RuntimeException {

    /** Singleton instance for throwing within a filter. */
    public static final org.eclipse.jgit.errors.StopWalkException INSTANCE = new org.eclipse.jgit.errors.StopWalkException();

    private static final long serialVersionUID = 1L;

    private StopWalkException() {
        super(null, null, false, false);
    }

}
