/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.root;

import android.content.Context;
import android.content.Intent;
import android.os.ResultReceiver;

import java.io.Serializable;
import java.util.List;

public class RootCommands implements Serializable {
    public static final String RESULT_RECEIVER = "RootCommandResultReceiver";

    private final List<String> commands;

    public RootCommands(List<String> commands){

        this.commands = commands;
    }

    public List<String> getCommands() {
        return this.commands;
    }

    public static boolean execute(Context context, List<String> commands, @RootCommandsMark int mark) {
        return execute(context, commands, mark, null);
    }

    public static boolean execute(Context context, List<String> commands, @RootCommandsMark int mark,
                                  ResultReceiver receiver) {
        RootCommands rootCommands = new RootCommands(commands);
        Intent intent = new Intent(context, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", mark);
        if (receiver != null) intent.putExtra(RESULT_RECEIVER, receiver);
        return RootExecService.performAction(context, intent);
    }
}
