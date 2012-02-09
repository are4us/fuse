/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.commands;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.fusesource.fabric.api.Container;
import org.fusesource.fabric.commands.support.FabricCommand;

@Command(name = "container-delete", scope = "fabric", description = "Delete an existing container")
public class ContainerDelete extends FabricCommand {

    @Argument(index = 0, description = "The name of the container", multiValued = false, required = true)
    private String name;

    @Override
    protected Object doExecute() throws Exception {
        Container found = getContainer(name);
        if (found == null) {
            throw new IllegalArgumentException("Container " + name + " does not exist.");
        }
        found.destroy();
        return null;
    }

}