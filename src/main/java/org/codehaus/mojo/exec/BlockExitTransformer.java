package org.codehaus.mojo.exec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import org.apache.maven.plugin.logging.Log;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ASM9;

public class BlockExitTransformer implements ClassFileTransformer {

    private final URLClassLoader classLoader;

    private final Log logger;

    BlockExitTransformer(URLClassLoader classLoader, Log logger) {
        this.classLoader = classLoader;
        this.logger = logger;
    }

    @Override
    public byte[] transform(
            final ClassLoader loader,
            final String className,
            final Class<?> classBeingRedefined,
            final ProtectionDomain protectionDomain,
            final byte[] classfileBuffer)
            throws IllegalClassFormatException {
        try {
            final ClassReader reader = new ClassReader(classfileBuffer);
            final ClassWriter writer = createClassWriter();
            final SystemExitOverrideVisitor visitor = new SystemExitOverrideVisitor(writer);
            reader.accept(visitor, EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (final RuntimeException re) { // too old asm for ex, ignore these classes to not block the rest
            logger.warn("Unable to transform class " + className + " : " + re.getMessage());
            return null;
        }
    }

    /**
     * Creates a new {@link ClassWriter} that uses the dedicated  {@link ClassLoader} of this transformer.
     * <p>
     * For bigger and more complicated classes {@link ClassWriter}
     * requires access to classloader where can found classes used in transformed class.
     *
     * @return a new {@link ClassWriter}
     */
    private ClassWriter createClassWriter() {
        return new ClassWriter(COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return classLoader;
            }
        };
    }

    public void close() throws IOException {
        classLoader.close();
    }

    private static class SystemExitOverrideVisitor extends ClassVisitor {
        private static final String SYSTEM_REPLACEMENT =
                SystemExitManager.class.getName().replace('.', '/');

        private SystemExitOverrideVisitor(final ClassVisitor visitor) {
            super(ASM9, visitor);
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            return new GeneratorAdapter(
                    ASM9,
                    super.visitMethod(access, name, descriptor, signature, exceptions),
                    access,
                    name,
                    descriptor) {
                @Override
                public void visitMethodInsn(
                        final int opcode,
                        final String owner,
                        final String name,
                        final String descriptor,
                        final boolean isInterface) {
                    if (owner.equals("java/lang/System") && name.equals("exit")) {
                        mv.visitMethodInsn(opcode, SYSTEM_REPLACEMENT, name, descriptor, isInterface);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                }
            };
        }
    }
}
