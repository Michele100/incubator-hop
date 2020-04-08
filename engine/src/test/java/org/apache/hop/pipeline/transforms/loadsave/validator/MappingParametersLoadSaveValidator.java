/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package org.apache.hop.pipeline.transforms.loadsave.validator;

import org.apache.hop.pipeline.transforms.mapping.MappingParameters;

import java.util.Random;
import java.util.UUID;

public class MappingParametersLoadSaveValidator implements IFieldLoadSaveValidator<MappingParameters> {
  final Random rand = new Random();

  @Override
  public MappingParameters getTestObject() {
    MappingParameters rtn = new MappingParameters();
    rtn.setVariable( new String[] {
      UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()
    } );
    rtn.setInputField( new String[] {
      UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()
    } );
    rtn.setInheritingAllVariables( rand.nextBoolean() );
    return rtn;
  }

  @Override
  public boolean validateTestObject( MappingParameters testObject, Object actual ) {
    if ( !( actual instanceof MappingParameters ) ) {
      return false;
    }
    MappingParameters actualInput = (MappingParameters) actual;
    return ( testObject.getXml().equals( actualInput.getXml() ) );
  }
}
