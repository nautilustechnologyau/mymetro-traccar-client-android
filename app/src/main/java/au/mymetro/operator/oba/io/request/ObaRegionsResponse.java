/*
 * Copyright 2023 Nautilus Technology
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
package au.mymetro.operator.oba.io.request;

import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.io.elements.ObaRegionElement;

public class ObaRegionsResponse extends ObaResponse {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaRegionElement[] list = ObaRegionElement.EMPTY_ARRAY;

    }

    private final Data data;

    private ObaRegionsResponse() {
        data = Data.EMPTY_OBJECT;
    }

    public ObaRegion[] getRegions() {
        return data.list;
    }
}
