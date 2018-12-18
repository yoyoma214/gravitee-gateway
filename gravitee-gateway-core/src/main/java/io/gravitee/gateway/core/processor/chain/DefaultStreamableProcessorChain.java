/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.core.processor.chain;

import io.gravitee.gateway.core.processor.StreamableProcessor;

import java.util.Iterator;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultStreamableProcessorChain<T, S, H extends StreamableProcessor<T, S>> extends AbstractStreamableProcessorChain<T, S, H> {

    private final List<H> processors;

    private Iterator<H> processorIterator;

    public DefaultStreamableProcessorChain(List<H> processors) {
        this.processors = processors;
        this.reset();
    }

    @Override
    protected H next(T data) {
        return next();
    }

    @Override
    public void doOnSuccess(T data) {
        resultHandler.handle(last);
    }

    @Override
    protected void reset() {
        this.processorIterator = processors.iterator();
    }

    @Override
    public boolean hasNext() {
        return processorIterator.hasNext();
    }

    @Override
    public H next() {
        return processorIterator.next();
    }
}
