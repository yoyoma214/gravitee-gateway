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

import io.gravitee.gateway.core.processor.Processor;

import java.util.Iterator;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultProcessorChain<T> extends AbstractProcessorChain<T, T, Processor<T>> {

    private final Iterator<Processor<T>> processorIterator;

    public DefaultProcessorChain(List<Processor<T>> processors) {
        this.processorIterator = processors.iterator();
    }

    @Override
    public boolean hasNext() {
        return processorIterator.hasNext();
    }

    @Override
    public Processor<T> next() {
        return processorIterator.next();
    }

    @Override
    protected Processor<T> next(T data) {
        return next();
    }

    @Override
    public void doOnSuccess(T data) {
        resultHandler.handle(data);
    }
}