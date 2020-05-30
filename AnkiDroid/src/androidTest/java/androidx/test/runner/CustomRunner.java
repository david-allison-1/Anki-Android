/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package androidx.test.runner;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.multidex.MultiDex;
import androidx.test.internal.runner.MultiDexRequestBuilder;
import androidx.test.internal.runner.TestRequestBuilder;

public class CustomRunner extends AndroidJUnitRunner {

    @Override
    public void onCreate(Bundle arguments)
    {
        MultiDex.installInstrumentation(this.getContext(), getTargetContext());
        super.onCreate(arguments);
    }

    @Override
    TestRequestBuilder createTestRequestBuilder(Instrumentation instr, Bundle arguments) {
        return new MultiDexRequestBuilder(instr, arguments, this.getTargetContext());
    }
}
