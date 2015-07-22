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
package io.gravitee.gateway.core.policy;

import io.gravitee.gateway.api.policy.Policy;
import io.gravitee.gateway.api.policy.PolicyConfiguration;
import io.gravitee.gateway.core.policy.impl.PolicyFactoryImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;

import static org.mockito.Mockito.*;


/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class PolicyFactoryTest {

    private PolicyFactory policyFactory;

    private PolicyConfigurationFactory policyConfigurationFactory;

    @Before
    public void setUp() {
        policyFactory = new PolicyFactoryImpl();
        policyConfigurationFactory = mock(PolicyConfigurationFactory.class);
        ((PolicyFactoryImpl) policyFactory).setPolicyConfigurationFactory(policyConfigurationFactory);
    }

    @Test
    public void createPolicyWithConfigurationAndWithoutConfigurationData() {
        PolicyDefinition definition = getPolicyDefinitionWithConfiguration();
        Policy policy = policyFactory.create(definition, null);

        verify(policyConfigurationFactory, never()).create(any(), anyString());
        Assert.assertNull(policy);
    }

    @Test
    public void createPolicyWithoutConfigurationAndWithoutConfigurationData() {
        PolicyDefinition definition = getPolicyDefinitionWithoutConfiguration();
        Policy policy = policyFactory.create(definition, null);

        verify(policyConfigurationFactory, never()).create(any(), anyString());
        Assert.assertNotNull(policy);
    }

    @Test
    public void createPolicyWithConfigurationAndConfigurationData() {
        PolicyDefinition definition = getPolicyDefinitionWithConfiguration();
        Policy policy = policyFactory.create(definition, "{}");

        verify(policyConfigurationFactory, times(1)).create(any(), anyString());
        Assert.assertNotNull(policy);
    }

    @Test
    public void createPolicyWithoutConfigurationAndWithConfigurationData() {
        PolicyDefinition definition = getPolicyDefinitionWithoutConfiguration();
        Policy policy = policyFactory.create(definition, "{}");

        verify(policyConfigurationFactory, never()).create(any(), anyString());
        Assert.assertNotNull(policy);
    }

    private PolicyDefinition getPolicyDefinitionWithConfiguration() {
        return new PolicyDefinition() {
            @Override
            public String id() {
                return null;
            }

            @Override
            public String name() {
                return "my-policy";
            }

            @Override
            public String description() {
                return null;
            }

            @Override
            public String version() {
                return null;
            }

            @Override
            public Class<? extends Policy> policy() {
                return DummyPolicy.class;
            }

            @Override
            public Class<? extends PolicyConfiguration> configuration() {
                return DummyPolicyConfiguration.class;
            }

            @Override
            public List<URL> getClassPathElements() {
                return null;
            }

            @Override
            public Method onRequestMethod() {
                return null;
            }

            @Override
            public Method onResponseMethod() {
                return null;
            }
        };
    }

    private PolicyDefinition getPolicyDefinitionWithoutConfiguration() {
        return new PolicyDefinition() {
            @Override
            public String id() {
                return null;
            }

            @Override
            public String name() {
                return "my-policy";
            }

            @Override
            public String description() {
                return null;
            }

            @Override
            public String version() {
                return null;
            }

            @Override
            public Class<? extends Policy> policy() {
                return DummyPolicy.class;
            }

            @Override
            public Class<? extends PolicyConfiguration> configuration() {
                return null;
            }

            @Override
            public List<URL> getClassPathElements() {
                return null;
            }

            @Override
            public Method onRequestMethod() {
                return null;
            }

            @Override
            public Method onResponseMethod() {
                return null;
            }
        };
    }

    class DummyPolicyConfiguration implements PolicyConfiguration {

    }
}
