/*
 * Copyright 2018-2021 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.parquetstore.query;

import org.apache.commons.lang3.StringUtils;

import uk.gov.gchq.gaffer.commonutil.ToStringBuilder;
import uk.gov.gchq.gaffer.data.element.id.ElementId;

public class ParquetEntitySeed extends ParquetElementSeed {

    private final Object[] seed;

    public ParquetEntitySeed(final ElementId elementId, final Object[] seed) {
        this.elementId = elementId;
        this.seed = seed;
    }

    public Object[] getSeed() {
        return seed;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("seed", StringUtils.join(seed, ','))
                .build();
    }
}
