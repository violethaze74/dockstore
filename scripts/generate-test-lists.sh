#!/bin/bash

#
# Copyright 2022 OICR and UCSC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This file generates a list of test files for each profile

set -o errexit
set -o nounset
set -o pipefail

# Generate list of bitbucket tests
grep -rnwl . -e "BitBucketTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " > temp.txt #bitbuckettests.txt
> bitbuckettests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >>  bitbuckettests.txt
done
# Generate list of non-confidential tests
grep -rnwl . -e "NonConfidentialTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " > temp.txt
> nonconfidentialtests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >>  nonconfidentialtests.txt
done

# Generate list of LanguageParsingTest
grep -rnwl . -e "LanguageParsingTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " > temp.txt
> languageparsingtests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >>  languageparsingtests.txt
done

# Generate list of unit tests
grep -rnwl  . -Le "BitBucketTest.class\|LanguageParsingTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " >  temp.txt
> unit-tests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >>  unit-tests.txt
done

# Generate list of workflow tests
grep -rnwl . -e "WorkflowTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " > temp.txt
> workflowtests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >>  workflowtests.txt
done

# Generate list of tool tests
grep -rnwl . -e "ToolTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " > temp.txt
> tooltests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >> tooltests.txt
done

# Generate list of integtration tests
grep -rnwl  . -Le "ToolTest.class\|WorkflowTest.class\|SlowTest.class\|RegressionTest.class\|NonConfidentialTest.class\|LanguageParsingTest.class\|BitBucketTest.class" | grep -E "IT*.java|Test*.java" | grep -v ./dockstore-common/src/main/java/io/dockstore/common/ | tr "\n" " " >  temp.txt
> integration-tests.txt
for file in $(cat temp.txt)
do
  basename $file |  sed -e "s/.java/,/" >> integration-tests.txt
done