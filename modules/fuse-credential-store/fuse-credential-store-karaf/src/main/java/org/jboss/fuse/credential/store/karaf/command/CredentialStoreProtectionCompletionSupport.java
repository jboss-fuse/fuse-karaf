/**
 *  Copyright 2005-2017 Red Hat, Inc.
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
package org.jboss.fuse.credential.store.karaf.command;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.jboss.fuse.credential.store.karaf.util.ProtectionType;

/**
 * A {@link Completer} that auto completes parameters for {@link org.wildfly.security.credential.source.CredentialSource}
 * that is used to protect the {@link org.wildfly.security.credential.store.CredentialStore}.
 * Requires that the type of protection ({@code -p} or {@code --protection-type} is specified
 * beforehand, as the parameters required by each {@link ProtectionType} might be different.
 */
@Service
public class CredentialStoreProtectionCompletionSupport implements Completer {

    /**
     * Returns the option part of the {@code option=value} pair.
     *
     * @param argument
     *            the whole argument, possibly in option=value syntax
     * @return just the option part, never null
     */
    static String optionOf(final String argument) {
        if (argument == null) {
            return "";
        }

        final String argumentTrimmed = argument.trim();

        final int optionValueSeparatorIdx = argumentTrimmed.indexOf('=');

        if (optionValueSeparatorIdx == 0) {
            return "";
        }

        if ((optionValueSeparatorIdx > 0) && (optionValueSeparatorIdx < argumentTrimmed.length())) {
            return argumentTrimmed.substring(0, optionValueSeparatorIdx).trim();
        }

        return argumentTrimmed;
    }

    /**
     * Returns all option values from the supplied arguments. Given an array of string arguments given by the user on
     * the command line, this method selects all {@code -k} or {@code --protection-attributes} argument values and
     * returns only the option from the {@code option=value} pair.
     *
     * @param arguments
     *            array of arguments from the command line
     * @return all protection options used
     */
    static Set<String> usedOptions(final String... arguments) {
        final Set<String> ret = new HashSet<>();

        for (int i = 0; i < arguments.length; i++) {
            final String argument = arguments[i];

            final boolean isProtectionAttributeOption = "-k".equals(argument)
                || "--protection-attributes".equals(argument);

            final boolean hasMoreArguments = arguments.length > (i + 1);

            if (isProtectionAttributeOption && hasMoreArguments && !arguments[i + 1].startsWith("-")) {
                ret.add(optionOf(arguments[i + 1]));
            }
        }

        return ret;
    }

    /**
     * Performs completion for any {@code -k} or {@code --protection-attributes} parameters firstly by suggesting an
     * option part, then providing completion for values for a specific option.
     *
     * @param session
     *            the current {@link Session}
     * @param commandLine
     *            the pre-parsed {@link CommandLine}
     * @param candidates
     *            a list to fill with possible completion candidates
     * @return the index of the{@link CommandLine} for which the completion will be relative
     */
    @Override
    public int complete(final Session session, final CommandLine commandLine, final List<String> candidates) {
        final String[] arguments = commandLine.getArguments();

        int protectionTypeIdx = -1;
        for (int i = 0; i < arguments.length; i++) {
            final String argument = arguments[i];
            if ("-p".equals(argument) || "--protection-type".equals(argument)) {
                protectionTypeIdx = i;
                break;
            }
        }

        // do we have protection type specified
        if ((protectionTypeIdx < 0) || (arguments.length <= (protectionTypeIdx + 1))) {
            return -1;
        }

        // parse the protection type argument
        final String protectionTypeString = arguments[protectionTypeIdx + 1];
        final ProtectionType protectionType;
        try {
            protectionType = ProtectionType.valueOf(protectionTypeString);
        } catch (final IllegalArgumentException e) {
            return -1;
        }

        // these are supported options for the specified protection type, we sort them for binarySearch below
        final String[] supportedOptions = Arrays.stream(protectionType.getSupportedOptions()).sorted()
                .toArray(String[]::new);

        // the user may have already chosen an option ("option=") part
        final String option = optionOf(commandLine.getCursorArgument());

        if (!option.isEmpty() && (Arrays.binarySearch(supportedOptions, option) >= 0)) {
            // if the user has chosen the option part, provide completion on the value part
            final String[] options = Arrays.stream(protectionType.getOptionValuesFor(option)).map(o -> option + "=" + o)
                    .toArray(String[]::new);

            return new StringsCompleter(options).complete(session, commandLine, candidates);
        }

        final Set<String> usedOptions = usedOptions(arguments);

        // use supported options without any used options
        final int complete = new StringsCompleter(Arrays.stream(supportedOptions).filter(o -> !usedOptions.contains(o))
                .map(o -> o + "=").toArray(String[]::new)).complete(session, commandLine, candidates);

        for (final ListIterator<String> i = candidates.listIterator(); i.hasNext();) {
            String candidate = i.next();

            if (candidate.endsWith("= ")) {
                i.set(candidate.substring(0, candidate.length() - 1));
            }
        }

        return complete;
    }

}
