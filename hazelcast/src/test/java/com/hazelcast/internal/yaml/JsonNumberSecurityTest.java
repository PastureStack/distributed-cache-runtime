/*
 * Copyright (c) 2008-2026, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.internal.yaml;

import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class JsonNumberSecurityTest {
    @Test
    public void oversizedNumericTokensRemainStrings() {
        String digits = "9".repeat(10000);
        for (String value : new String[]{digits, "-" + digits, digits + ".1"}) {
            assertEquals(value, new JSONObject("{\"n\":" + value + "}").get("n"));
            assertEquals(value, new JSONArray("[" + value + "]").get(0));
            assertEquals(value, XML.toJSONObject("<n>" + value + "</n>").get("n"));
        }
    }

    @Test
    public void shortExponentCannotExpandIntoUnboundedInteger() {
        assertEquals(BigInteger.TEN,
                new JSONObject("{\"n\":1e100000}").optBigInteger("n", BigInteger.TEN));
    }

    @Test
    public void normalConfigurationValuesRetainTypes() {
        JSONObject object = new JSONObject("{\"port\":5701,\"factor\":1.25,\"enabled\":true,\"name\":\"member\"}");
        assertEquals(5701, object.getInt("port"));
        assertEquals(1.25, object.getDouble("factor"), 0.0);
        assertEquals(true, object.getBoolean("enabled"));
        assertEquals("member", object.getString("name"));
        assertEquals(BigInteger.valueOf(1000), new JSONObject("{\"n\":1e3}").getBigInteger("n"));
    }
}
