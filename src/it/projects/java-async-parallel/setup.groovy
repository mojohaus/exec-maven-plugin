/*
 * Copyright MojoHaus and Contributors
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

// Free ports for the programs of the two modules, passed to Maven as user properties.
def ports = []
2.times {
    def socket = new ServerSocket(0)
    ports << socket.localPort
    socket.close()
}

def mvn = new File(basedir, '.mvn')
mvn.mkdirs()
new File(mvn, 'maven.config').text = "-Dit.port.a=${ports[0]}\n-Dit.port.b=${ports[1]}\n"
true
