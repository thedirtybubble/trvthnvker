package com.dudebro.trvthnvker.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class TelemetryTransformer implements ClassFileTransformer {

    private static final byte[] SNOOP_BYTES = new byte[]{
        's', 'n', 'o', 'o', 'p', '.', 'm', 'i', 'n', 'e', 'c', 'r', 'a', 'f', 't', '.', 'n', 'e', 't'
    };

    private final boolean stripRealms;
    private final boolean stripChatKeys;

    public TelemetryTransformer(boolean stripRealms, boolean stripChatKeys) {
        this.stripRealms = stripRealms;
        this.stripChatKeys = stripChatKeys;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) {
        if (className == null) {
            return classfileBuffer;
        }

        if (className.startsWith("java/")
                || className.startsWith("javax/")
                || className.startsWith("sun/")
                || className.startsWith("com/sun/")
                || className.startsWith("jdk/")
                || className.startsWith("net/defensive/trvthnvker/")
                || className.startsWith("org/apache/logging/")
                || className.startsWith("org/slf4j/")
                || className.startsWith("net/fabricmc/")
                || className.startsWith("org/spongepowered/")
                || className.startsWith("io/netty/")
                || className.startsWith("org/lwjgl/")) {
            return classfileBuffer;
        }

        try {
            final boolean isSnooper = containsBytes(classfileBuffer, SNOOP_BYTES);
            final boolean isAuthLibUserApi = className.contains("UserApiService");
            final boolean isTelemetrySender = className.endsWith("TelemetryEventSender")
                    || className.endsWith("ClientTelemetryManager")
                    || className.endsWith("WorldSessionTelemetryManager");
            final boolean isRealmsClient = this.stripRealms && className.endsWith("RealmsClient");
            final boolean isRealmsFetcher = this.stripRealms && className.endsWith("RealmsDataFetcher");
            final boolean isMojmapTitleScreen = className.equals("net/minecraft/client/gui/screens/TitleScreen");
            final boolean isTitleScreen = isMojmapTitleScreen
                    || className.endsWith("TitleScreen")
                    || className.endsWith("MainMenuScreen")
                    || className.endsWith("GuiMainMenu")
                    || className.equals("net/minecraft/class_442");
            final boolean isKeyPairManager = this.stripChatKeys && (
                    className.endsWith("AccountProfileKeyPairManager")
                    || className.endsWith("ProfileKeyPairManager")
                    || className.endsWith("ProfileKeysImpl")
                    || className.endsWith("ProfileKeys")
                    || className.equals("net/minecraft/class_7434")
            );
            final boolean isPrideFlag = className.endsWith("/pride/PrideFlag")
                    || className.contains("/pride/shape/");
            final boolean isPrideFlags = className.endsWith("/pride/PrideFlags");
            final boolean isClientLanguage = className.endsWith("ClientLanguage")
                    || className.equals("net/minecraft/class_1078");
            final boolean isSplashRenderer = className.endsWith("SplashRenderer")
                    || className.endsWith("SplashTextRenderer")
                    || className.equals("net/minecraft/class_8519");

            if (!isSnooper && !isAuthLibUserApi && !isTelemetrySender && !isRealmsClient && !isRealmsFetcher
                    && !isTitleScreen && !isKeyPairManager && !isPrideFlag && !isPrideFlags && !isClientLanguage && !isSplashRenderer) {
                return classfileBuffer;
            }

            int majorVersion = ((classfileBuffer[6] & 0xFF) << 8) | (classfileBuffer[7] & 0xFF);
            final int originalVersion = majorVersion;

            byte[] readerBuffer = classfileBuffer;
            if (majorVersion > 65) {
                readerBuffer = classfileBuffer.clone();
                readerBuffer[6] = 0;
                readerBuffer[7] = 65;
            }

            ClassReader cr = new ClassReader(readerBuffer);
            final boolean[] modified = new boolean[]{false};

            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                        return "java/lang/Object";
                    }
                    ClassLoader cl = loader != null ? loader : getClass().getClassLoader();
                    try {
                        Class<?> c1 = Class.forName(type1.replace('/', '.'), false, cl);
                        Class<?> c2 = Class.forName(type2.replace('/', '.'), false, cl);
                        if (c1.isAssignableFrom(c2)) {
                            return type1;
                        }
                        if (c2.isAssignableFrom(c1)) {
                            return type2;
                        }
                        if (c1.isInterface() || c2.isInterface()) {
                            return "java/lang/Object";
                        }
                        do {
                            c1 = c1.getSuperclass();
                            if (c1 == null) {
                                return "java/lang/Object";
                            }
                        } while (!c1.isAssignableFrom(c2));
                        return c1.getName().replace('.', '/');
                    } catch (Throwable ignored) {
                        return "java/lang/Object";
                    }
                }
            };

            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(originalVersion, access, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                        return mv;
                    }

                    if (isAuthLibUserApi && "newTelemetrySession".equals(name) && descriptor.endsWith("TelemetrySession;")) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitFieldInsn(
                                        Opcodes.GETSTATIC,
                                        "com/mojang/authlib/minecraft/TelemetrySession",
                                        "DISABLED",
                                        "Lcom/mojang/authlib/minecraft/TelemetrySession;"
                                );
                                targetMv.visitInsn(Opcodes.ARETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(1, 2);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (stripChatKeys && isAuthLibUserApi && "getKeyPair".equals(name)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitInsn(Opcodes.ACONST_NULL);
                                targetMv.visitInsn(Opcodes.ARETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(1, 1);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isKeyPairManager) {
                        if (descriptor.endsWith("Ljava/util/concurrent/CompletableFuture;")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/util/Optional",
                                            "empty",
                                            "()Ljava/util/Optional;",
                                            false
                                    );
                                    targetMv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "java/util/concurrent/CompletableFuture",
                                            "completedFuture",
                                            "(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",
                                            false
                                    );
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(1, 2);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if (name.contains("fetch") || descriptor.endsWith("ProfileKeyPair;")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.ACONST_NULL);
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(1, 2);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }
                    }

                    if (isTelemetrySender && "send".equals(name) && descriptor.endsWith(")V")) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitInsn(Opcodes.RETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(0, 3);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isSnooper && ("start".equals(name) || "run".equals(name)) && "()V".equals(descriptor)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitInsn(Opcodes.RETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(0, 1);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isRealmsClient && "fetchFeatureFlags".equals(name)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/util/Collections",
                                        "emptySet",
                                        "()Ljava/util/Set;",
                                        false
                                );
                                targetMv.visitInsn(Opcodes.ARETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(1, 1);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isRealmsClient && "clientCompatible".equals(name)) {
                        final org.objectweb.asm.Type retType = org.objectweb.asm.Type.getReturnType(descriptor);
                        if (retType.getSort() == org.objectweb.asm.Type.OBJECT) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            final String enumOwner = retType.getInternalName();
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitFieldInsn(
                                            Opcodes.GETSTATIC,
                                            enumOwner,
                                            "OTHER",
                                            "L" + enumOwner + ";"
                                    );
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(1, 2);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }
                    }

                    if (isRealmsClient && "execute".equals(name)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitTypeInsn(Opcodes.NEW, "com/mojang/realmsclient/exception/RealmsServiceException");
                                targetMv.visitInsn(Opcodes.DUP);
                                targetMv.visitIntInsn(Opcodes.SIPUSH, 503);
                                targetMv.visitLdcInsn("Realms service disabled by agent switch");
                                targetMv.visitMethodInsn(
                                        Opcodes.INVOKESPECIAL,
                                        "com/mojang/realmsclient/exception/RealmsServiceException",
                                        "<init>",
                                        "(ILjava/lang/String;)V",
                                        false
                                );
                                targetMv.visitInsn(Opcodes.ATHROW);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(4, 2);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isRealmsFetcher && ("init".equals(name) || "start".equals(name) || "scheduleTasks".equals(name)) && "()V".equals(descriptor)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitInsn(Opcodes.RETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(0, 1);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (stripRealms && isMojmapTitleScreen) {
                        if (name.contains("RealmsNotification")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.ICONST_0);
                                    targetMv.visitInsn(Opcodes.IRETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(1, 1);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if ("init".equals(name) && "()V".equals(descriptor)) {
                            modified[0] = true;
                            final MethodVisitor nextMv = mv;
                            mv = new MethodVisitor(Opcodes.ASM9, nextMv) {
                                @Override
                                public void visitInsn(int opcode) {
                                    if (opcode == Opcodes.RETURN) {
                                        super.visitVarInsn(Opcodes.ALOAD, 0);
                                        super.visitMethodInsn(
                                                Opcodes.INVOKESPECIAL,
                                                "net/minecraft/client/gui/screens/TitleScreen",
                                                "__tlm_shiftWidgets",
                                                "()V",
                                                false
                                        );
                                    }
                                    super.visitInsn(opcode);
                                }
                            };
                        } else {
                            final MethodVisitor nextMv = mv;
                            mv = new MethodVisitor(Opcodes.ASM9, nextMv) {
                                private boolean isRealmsButton = false;

                                @Override
                                public void visitLdcInsn(Object value) {
                                    if ("menu.online".equals(value)) {
                                        isRealmsButton = true;
                                    }
                                    super.visitLdcInsn(value);
                                }

                                @Override
                                public void visitMethodInsn(
                                        int opcode,
                                        String owner,
                                        String mName,
                                        String mDesc,
                                        boolean isInterface
                                ) {
                                    if (isRealmsButton && opcode == Opcodes.INVOKEVIRTUAL) {
                                        if (mName.contains("Widget") || mName.contains("Child") || mName.contains("Button") || mName.contains("add")) {
                                            isRealmsButton = false;
                                            modified[0] = true;
                                            super.visitInsn(Opcodes.DUP);
                                            super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/client/gui/components/AbstractWidget");
                                            super.visitInsn(Opcodes.DUP);
                                            super.visitInsn(Opcodes.ICONST_0);
                                            super.visitFieldInsn(Opcodes.PUTFIELD, "net/minecraft/client/gui/components/AbstractWidget", "visible", "Z");
                                            super.visitInsn(Opcodes.ICONST_0);
                                            super.visitFieldInsn(Opcodes.PUTFIELD, "net/minecraft/client/gui/components/AbstractWidget", "active", "Z");
                                        }
                                    }
                                    super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                                }
                            };
                        }
                    }


                    if (isPrideFlag) {
                        if (("render".equals(name) || "extractRenderState".equals(name)) && descriptor.endsWith(")V")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.RETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(0, 7);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if (descriptor.endsWith(")Ljava/util/List;")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.ICONST_5);
                                    targetMv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Integer");
                                    
                                    // TRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTSTRANSRIGHTS
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_0);
                                    targetMv.visitLdcInsn(0x005BCEFA); // Transparent Light Blue
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                    targetMv.visitInsn(Opcodes.AASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_1);
                                    targetMv.visitLdcInsn(0x00F5A9B8); // Transparent Pink
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                    targetMv.visitInsn(Opcodes.AASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_2);
                                    targetMv.visitLdcInsn(0x00FFFFFF); // Transparent White
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                    targetMv.visitInsn(Opcodes.AASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_3);
                                    targetMv.visitLdcInsn(0x00F5A9B8); // Transparent Pink
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                    targetMv.visitInsn(Opcodes.AASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_4);
                                    targetMv.visitLdcInsn(0x005BCEFA); // Transparent Light Blue
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                                    targetMv.visitInsn(Opcodes.AASTORE);
                                    
                                    targetMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(5, 1);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if (descriptor.endsWith(")[I")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.ICONST_5);
                                    targetMv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_0);
                                    targetMv.visitLdcInsn(0x005BCEFA);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_1);
                                    targetMv.visitLdcInsn(0x00F5A9B8);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_2);
                                    targetMv.visitLdcInsn(0x00FFFFFF);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_3);
                                    targetMv.visitLdcInsn(0x00F5A9B8);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_4);
                                    targetMv.visitLdcInsn(0x005BCEFA);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(5, 1);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if (descriptor.endsWith(")Lit/unimi/dsi/fastutil/ints/IntList;")) {
                            modified[0] = true;
                            final MethodVisitor targetMv = mv;
                            return new MethodVisitor(Opcodes.ASM9, null) {
                                @Override
                                public void visitCode() {
                                    targetMv.visitCode();
                                    targetMv.visitInsn(Opcodes.ICONST_5);
                                    targetMv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_0);
                                    targetMv.visitLdcInsn(0x005BCEFA);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_1);
                                    targetMv.visitLdcInsn(0x00F5A9B8);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_2);
                                    targetMv.visitLdcInsn(0x00FFFFFF);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_3);
                                    targetMv.visitLdcInsn(0x00F5A9B8);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitInsn(Opcodes.DUP);
                                    targetMv.visitInsn(Opcodes.ICONST_4);
                                    targetMv.visitLdcInsn(0x005BCEFA);
                                    targetMv.visitInsn(Opcodes.IASTORE);
                                    
                                    targetMv.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "it/unimi/dsi/fastutil/ints/IntList",
                                            "of",
                                            "([I)Lit/unimi/dsi/fastutil/ints/IntList;",
                                            true
                                    );
                                    targetMv.visitInsn(Opcodes.ARETURN);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    targetMv.visitMaxs(5, 1);
                                }

                                @Override
                                public void visitEnd() {
                                    targetMv.visitEnd();
                                }
                            };
                        }

                        if (name.toLowerCase(java.util.Locale.ROOT).contains("color")) {
                            if (descriptor.endsWith(")I")) {
                                modified[0] = true;
                                final MethodVisitor targetMv = mv;
                                return new MethodVisitor(Opcodes.ASM9, null) {
                                    @Override
                                    public void visitCode() {
                                        targetMv.visitCode();
                                        targetMv.visitInsn(Opcodes.ICONST_0);
                                        targetMv.visitInsn(Opcodes.IRETURN);
                                    }

                                    @Override
                                    public void visitMaxs(int maxStack, int maxLocals) {
                                        targetMv.visitMaxs(1, 2);
                                    }

                                    @Override
                                    public void visitEnd() {
                                        targetMv.visitEnd();
                                    }
                                };
                            }
                            if (descriptor.endsWith(")Ljava/lang/Integer;")) {
                                modified[0] = true;
                                final MethodVisitor targetMv = mv;
                                return new MethodVisitor(Opcodes.ASM9, null) {
                                    @Override
                                    public void visitCode() {
                                        targetMv.visitCode();
                                        targetMv.visitInsn(Opcodes.ICONST_0);
                                        targetMv.visitMethodInsn(
                                                Opcodes.INVOKESTATIC,
                                                "java/lang/Integer",
                                                "valueOf",
                                                "(I)Ljava/lang/Integer;",
                                                false
                                        );
                                        targetMv.visitInsn(Opcodes.ARETURN);
                                    }

                                    @Override
                                    public void visitMaxs(int maxStack, int maxLocals) {
                                        targetMv.visitMaxs(1, 2);
                                    }

                                    @Override
                                    public void visitEnd() {
                                        targetMv.visitEnd();
                                    }
                                };
                            }
                        }
                    }

                    if (isPrideFlags && "isPrideMonth".equals(name) && "()Z".equals(descriptor)) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        return new MethodVisitor(Opcodes.ASM9, null) {
                            @Override
                            public void visitCode() {
                                targetMv.visitCode();
                                targetMv.visitInsn(Opcodes.ICONST_0);
                                targetMv.visitInsn(Opcodes.IRETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                targetMv.visitMaxs(1, 1);
                            }

                            @Override
                            public void visitEnd() {
                                targetMv.visitEnd();
                            }
                        };
                    }

                    if (isClientLanguage
                            && ("getOrDefault".equals(name) || "method_4679".equals(name) || name.startsWith("m_") || name.startsWith("func_"))
                            && descriptor.startsWith("(Ljava/lang/String;")
                            && descriptor.endsWith("Ljava/lang/String;")) {
                        modified[0] = true;
                        final String ownerClass = className;
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            ownerClass,
                                            "__tlm_filterPride",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                            false
                                    );
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    if (isSplashRenderer) {
                        modified[0] = true;
                        final MethodVisitor targetMv = mv;
                        final String ownerClass = className;
                        mv = new MethodVisitor(Opcodes.ASM9, targetMv) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                                if (opcode == Opcodes.PUTFIELD && fDesc.startsWith("L")) {
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            ownerClass,
                                            "__tlm_filterSplash",
                                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                                            false
                                    );
                                    if (!"Ljava/lang/Object;".equals(fDesc)) {
                                        super.visitTypeInsn(Opcodes.CHECKCAST, fDesc.substring(1, fDesc.length() - 1));
                                    }
                                }
                                super.visitFieldInsn(opcode, owner, fName, fDesc);
                                if (opcode == Opcodes.GETFIELD && fDesc.startsWith("L")) {
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            ownerClass,
                                            "__tlm_filterSplash",
                                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                                            false
                                    );
                                    if (!"Ljava/lang/Object;".equals(fDesc)) {
                                        super.visitTypeInsn(Opcodes.CHECKCAST, fDesc.substring(1, fDesc.length() - 1));
                                    }
                                }
                            }
                        };
                    } else if (isTitleScreen) {
                        final MethodVisitor targetMv = mv;
                        final String ownerClass = className;
                        mv = new MethodVisitor(Opcodes.ASM9, targetMv) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                                boolean isSplashField = "Ljava/lang/String;".equals(fDesc)
                                        && (fName.toLowerCase(java.util.Locale.ROOT).contains("splash")
                                            || fName.equals("field_73975_c")
                                            || fName.equals("field_18321")
                                            || fName.equals("field_44662"));
                                if (opcode == Opcodes.PUTFIELD && isSplashField) {
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            ownerClass,
                                            "__tlm_filterString",
                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                            false
                                    );
                                    modified[0] = true;
                                }
                                super.visitFieldInsn(opcode, owner, fName, fDesc);
                                if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) && isSplashField) {
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            ownerClass,
                                            "__tlm_filterString",
                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                            false
                                    );
                                    modified[0] = true;
                                }
                            }
                        };
                    }

                    return mv;
                }

                @Override
                public void visitEnd() {
                    if (isClientLanguage) {
                        MethodVisitor lmv = cv.visitMethod(
                                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                                "__tlm_filterPride",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                null,
                                null
                        );
                        lmv.visitCode();

                        Label lHasResult = new Label();
                        Label lCheck = new Label();
                        Label lDoReplace = new Label();
                        Label lReturnOrig = new Label();

                        lmv.visitVarInsn(Opcodes.ALOAD, 0);
                        lmv.visitJumpInsn(Opcodes.IFNONNULL, lHasResult);
                        lmv.visitVarInsn(Opcodes.ALOAD, 1);
                        lmv.visitVarInsn(Opcodes.ASTORE, 0);

                        lmv.visitLabel(lHasResult);
                        lmv.visitVarInsn(Opcodes.ALOAD, 0);
                        lmv.visitJumpInsn(Opcodes.IFNONNULL, lCheck);
                        lmv.visitInsn(Opcodes.ACONST_NULL);
                        lmv.visitInsn(Opcodes.ARETURN);

                        lmv.visitLabel(lCheck);
                        lmv.visitVarInsn(Opcodes.ALOAD, 0);
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
                        lmv.visitLdcInsn("pride");
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                        lmv.visitJumpInsn(Opcodes.IFNE, lDoReplace);

                        lmv.visitVarInsn(Opcodes.ALOAD, 1);
                        lmv.visitJumpInsn(Opcodes.IFNULL, lReturnOrig);
                        lmv.visitVarInsn(Opcodes.ALOAD, 1);
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
                        lmv.visitLdcInsn("pride");
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                        lmv.visitJumpInsn(Opcodes.IFNE, lDoReplace);

                        lmv.visitLabel(lReturnOrig);
                        lmv.visitVarInsn(Opcodes.ALOAD, 0);
                        lmv.visitInsn(Opcodes.ARETURN);

                        lmv.visitLabel(lDoReplace);
                        lmv.visitVarInsn(Opcodes.ALOAD, 0);
                        lmv.visitLdcInsn("(?i)pride[ _\\.]?month");
                        lmv.visitLdcInsn("Milk Month");
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        lmv.visitLdcInsn("(?i)pride");
                        lmv.visitLdcInsn("Milk");
                        lmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        lmv.visitInsn(Opcodes.ARETURN);

                        lmv.visitMaxs(0, 0);
                        lmv.visitEnd();
                    }

                    if (stripRealms && isMojmapTitleScreen) {
                        MethodVisitor smv = cv.visitMethod(
                                Opcodes.ACC_PRIVATE,
                                "__tlm_shiftWidgets",
                                "()V",
                                null,
                                null
                        );
                        smv.visitCode();

                        smv.visitVarInsn(Opcodes.ALOAD, 0);
                        smv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/client/gui/screens/Screen", "height", "I");
                        smv.visitInsn(Opcodes.ICONST_4);
                        smv.visitInsn(Opcodes.IDIV);
                        smv.visitIntInsn(Opcodes.BIPUSH, 100);
                        smv.visitInsn(Opcodes.IADD);
                        smv.visitVarInsn(Opcodes.ISTORE, 1);

                        smv.visitVarInsn(Opcodes.ALOAD, 0);
                        smv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/client/gui/screens/Screen", "height", "I");
                        smv.visitIntInsn(Opcodes.BIPUSH, 25);
                        smv.visitInsn(Opcodes.ISUB);
                        smv.visitVarInsn(Opcodes.ISTORE, 2);

                        smv.visitVarInsn(Opcodes.ALOAD, 0);
                        smv.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "net/minecraft/client/gui/screens/Screen",
                                "children",
                                "()Ljava/util/List;",
                                false
                        );
                        smv.visitMethodInsn(
                                Opcodes.INVOKEINTERFACE,
                                "java/util/List",
                                "iterator",
                                "()Ljava/util/Iterator;",
                                true
                        );
                        smv.visitVarInsn(Opcodes.ASTORE, 3);

                        Label loopStart = new Label();
                        Label loopEnd = new Label();

                        smv.visitLabel(loopStart);
                        smv.visitVarInsn(Opcodes.ALOAD, 3);
                        smv.visitMethodInsn(
                                Opcodes.INVOKEINTERFACE,
                                "java/util/Iterator",
                                "hasNext",
                                "()Z",
                                true
                        );
                        smv.visitJumpInsn(Opcodes.IFEQ, loopEnd);

                        smv.visitVarInsn(Opcodes.ALOAD, 3);
                        smv.visitMethodInsn(
                                Opcodes.INVOKEINTERFACE,
                                "java/util/Iterator",
                                "next",
                                "()Ljava/lang/Object;",
                                true
                        );
                        smv.visitVarInsn(Opcodes.ASTORE, 4);

                        smv.visitVarInsn(Opcodes.ALOAD, 4);
                        smv.visitTypeInsn(Opcodes.INSTANCEOF, "net/minecraft/client/gui/components/AbstractWidget");
                        smv.visitJumpInsn(Opcodes.IFEQ, loopStart);

                        smv.visitVarInsn(Opcodes.ALOAD, 4);
                        smv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/client/gui/components/AbstractWidget");
                        smv.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "net/minecraft/client/gui/components/AbstractWidget",
                                "getY",
                                "()I",
                                false
                        );
                        smv.visitVarInsn(Opcodes.ISTORE, 5);

                        smv.visitVarInsn(Opcodes.ILOAD, 5);
                        smv.visitVarInsn(Opcodes.ILOAD, 1);
                        smv.visitJumpInsn(Opcodes.IF_ICMPLE, loopStart);

                        smv.visitVarInsn(Opcodes.ILOAD, 5);
                        smv.visitVarInsn(Opcodes.ILOAD, 2);
                        smv.visitJumpInsn(Opcodes.IF_ICMPGE, loopStart);

                        smv.visitVarInsn(Opcodes.ALOAD, 4);
                        smv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/client/gui/components/AbstractWidget");
                        smv.visitVarInsn(Opcodes.ILOAD, 5);
                        smv.visitIntInsn(Opcodes.BIPUSH, 24);
                        smv.visitInsn(Opcodes.ISUB);
                        smv.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "net/minecraft/client/gui/components/AbstractWidget",
                                "setY",
                                "(I)V",
                                false
                        );
                        smv.visitJumpInsn(Opcodes.GOTO, loopStart);
						
                        smv.visitLabel(loopEnd);
                        smv.visitInsn(Opcodes.RETURN);
                        smv.visitMaxs(0, 0);
                        smv.visitEnd();
                    }

                    if (isSplashRenderer || isTitleScreen) {
                        MethodVisitor tmv = cv.visitMethod(
                                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                                "__tlm_filterString",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                null,
                                null
                        );
                        tmv.visitCode();
                        Label lNullStr = new Label();
                        Label lReturnMilk = new Label();
                        Label lReturnText = new Label();

                        tmv.visitVarInsn(Opcodes.ALOAD, 0);
                        tmv.visitJumpInsn(Opcodes.IFNULL, lNullStr);

                        tmv.visitVarInsn(Opcodes.ALOAD, 0);
                        tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
                        tmv.visitVarInsn(Opcodes.ASTORE, 1);

                        String[][] wittyRewrites = new String[][]{
                            {"be anti-racist", "(?i)be anti-racist!?", "Drink whole milk!"},
                            {"anti-racis", "(?i)anti-racis(t|m)", "dairy farming"},
                            {"allyship", "(?i)allyship", "pasteurization"},
                            {"bipoc voices", "(?i)bipoc voices", "cow moos"},
                            {"bipoc", "(?i)bipoc", "dairy cow"},
                            {"injustice", "(?i)injustice", "spilt milk"},
                            {"equality", "(?i)equality", "milk and cookies"},
                            {"lives matter", "(?i)\\b\\S+(?=\\s+lives matter)", "Milk"},
                            {"rights", "(?i)\\b\\S+(?=\\s+rights)", "Milk"}
                        };

                        for (int i = 0; i < wittyRewrites.length; i++) {
                            Label lSkip = new Label();
                            tmv.visitVarInsn(Opcodes.ALOAD, 1);
                            tmv.visitLdcInsn(wittyRewrites[i][0]);
                            tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                            tmv.visitJumpInsn(Opcodes.IFEQ, lSkip);

                            tmv.visitVarInsn(Opcodes.ALOAD, 0);
                            tmv.visitLdcInsn(wittyRewrites[i][1]);
                            tmv.visitLdcInsn(wittyRewrites[i][2]);
                            tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            tmv.visitVarInsn(Opcodes.ASTORE, 0);

                            tmv.visitVarInsn(Opcodes.ALOAD, 0);
                            tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
                            tmv.visitVarInsn(Opcodes.ASTORE, 1);

                            tmv.visitLabel(lSkip);
                        }

                        Label lSkipPride = new Label();
                        tmv.visitVarInsn(Opcodes.ALOAD, 1);
                        tmv.visitLdcInsn("pride");
                        tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                        tmv.visitJumpInsn(Opcodes.IFEQ, lSkipPride);

                        tmv.visitVarInsn(Opcodes.ALOAD, 0);
                        tmv.visitLdcInsn("(?i)pride[ _\\.]?month");
                        tmv.visitLdcInsn("Milk Month");
                        tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        tmv.visitLdcInsn("(?i)pride");
                        tmv.visitLdcInsn("Milk");
                        tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        tmv.visitVarInsn(Opcodes.ASTORE, 0);

                        tmv.visitVarInsn(Opcodes.ALOAD, 0);
                        tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
                        tmv.visitVarInsn(Opcodes.ASTORE, 1);

                        tmv.visitLabel(lSkipPride);

                        String[] milkKeywords = new String[]{
                            "lgbtq", "transgender", "gender", "aapi", "racism", "anti-racist", "anti-racism", "allyship", "bipoc"
                        };
                        for (int i = 0; i < milkKeywords.length; i++) {
                            tmv.visitVarInsn(Opcodes.ALOAD, 1);
                            tmv.visitLdcInsn(milkKeywords[i]);
                            tmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                            tmv.visitJumpInsn(Opcodes.IFNE, lReturnMilk);
                        }

                        tmv.visitJumpInsn(Opcodes.GOTO, lReturnText);

                        tmv.visitLabel(lReturnMilk);
                        tmv.visitLdcInsn("Got milk?");
                        tmv.visitInsn(Opcodes.ARETURN);

                        tmv.visitLabel(lReturnText);
                        tmv.visitVarInsn(Opcodes.ALOAD, 0);
                        tmv.visitInsn(Opcodes.ARETURN);

                        tmv.visitLabel(lNullStr);
                        tmv.visitInsn(Opcodes.ACONST_NULL);
                        tmv.visitInsn(Opcodes.ARETURN);

                        tmv.visitMaxs(0, 0);
                        tmv.visitEnd();
                    }
                    
                    if (isSplashRenderer) {
                        MethodVisitor pmv = cv.visitMethod(
                                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                                "__tlm_filterSplash",
                                "(Ljava/lang/Object;)Ljava/lang/Object;",
                                null,
                                null
                        );
                        pmv.visitCode();

                        Label lTryStart = new Label();
                        Label lTryEnd = new Label();
                        Label lCatchAll = new Label();
                        pmv.visitTryCatchBlock(lTryStart, lTryEnd, lCatchAll, "java/lang/Throwable");

                        Label lNotNull = new Label();
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitJumpInsn(Opcodes.IFNONNULL, lNotNull);
                        pmv.visitInsn(Opcodes.ACONST_NULL);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitLabel(lNotNull);
                        Label lNotString = new Label();
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/String");
                        pmv.visitJumpInsn(Opcodes.IFEQ, lNotString);

                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                        pmv.visitMethodInsn(Opcodes.INVOKESTATIC, className, "__tlm_filterString", "(Ljava/lang/String;)Ljava/lang/String;", false);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitLabel(lNotString);
                        pmv.visitLabel(lTryStart);

                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
                        pmv.visitLdcInsn("getString");
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 1);

                        pmv.visitVarInsn(Opcodes.ALOAD, 1);
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                        pmv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                        pmv.visitVarInsn(Opcodes.ASTORE, 2);

                        Label lCheckOrig = new Label();
                        pmv.visitVarInsn(Opcodes.ALOAD, 2);
                        pmv.visitJumpInsn(Opcodes.IFNONNULL, lCheckOrig);
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitLabel(lCheckOrig);
                        pmv.visitVarInsn(Opcodes.ALOAD, 2);
                        pmv.visitMethodInsn(Opcodes.INVOKESTATIC, className, "__tlm_filterString", "(Ljava/lang/String;)Ljava/lang/String;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 3);

                        Label lDiff = new Label();
                        pmv.visitVarInsn(Opcodes.ALOAD, 2);
                        pmv.visitVarInsn(Opcodes.ALOAD, 3);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                        pmv.visitJumpInsn(Opcodes.IFEQ, lDiff);
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitLabel(lDiff);
                        pmv.visitVarInsn(Opcodes.ALOAD, 1);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "getDeclaringClass", "()Ljava/lang/Class;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 4);

                        Label lTryLit = new Label();
                        Label lTryLitEnd = new Label();
                        Label lTryIntermediary = new Label();
                        Label lTryIntermediaryEnd = new Label();
                        Label lTrySrg = new Label();
                        Label lInvokeFactory = new Label();

                        pmv.visitTryCatchBlock(lTryLit, lTryLitEnd, lTryIntermediary, "java/lang/Throwable");
                        pmv.visitTryCatchBlock(lTryIntermediary, lTryIntermediaryEnd, lTrySrg, "java/lang/Throwable");

                        pmv.visitLabel(lTryLit);
                        pmv.visitVarInsn(Opcodes.ALOAD, 4);
                        pmv.visitLdcInsn("literal");
                        pmv.visitInsn(Opcodes.ICONST_1);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                        pmv.visitInsn(Opcodes.DUP);
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitLdcInsn(org.objectweb.asm.Type.getType("Ljava/lang/String;"));
                        pmv.visitInsn(Opcodes.AASTORE);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 5);
                        pmv.visitLabel(lTryLitEnd);
                        pmv.visitJumpInsn(Opcodes.GOTO, lInvokeFactory);

                        pmv.visitLabel(lTryIntermediary);
                        pmv.visitInsn(Opcodes.POP);
                        pmv.visitVarInsn(Opcodes.ALOAD, 4);
                        pmv.visitLdcInsn("method_43470");
                        pmv.visitInsn(Opcodes.ICONST_1);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                        pmv.visitInsn(Opcodes.DUP);
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitLdcInsn(org.objectweb.asm.Type.getType("Ljava/lang/String;"));
                        pmv.visitInsn(Opcodes.AASTORE);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 5);
                        pmv.visitLabel(lTryIntermediaryEnd);
                        pmv.visitJumpInsn(Opcodes.GOTO, lInvokeFactory);

                        pmv.visitLabel(lTrySrg);
                        pmv.visitInsn(Opcodes.POP);
                        pmv.visitVarInsn(Opcodes.ALOAD, 4);
                        pmv.visitLdcInsn("m_237113_");
                        pmv.visitInsn(Opcodes.ICONST_1);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                        pmv.visitInsn(Opcodes.DUP);
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitLdcInsn(org.objectweb.asm.Type.getType("Ljava/lang/String;"));
                        pmv.visitInsn(Opcodes.AASTORE);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                        pmv.visitVarInsn(Opcodes.ASTORE, 5);

                        pmv.visitLabel(lInvokeFactory);
                        pmv.visitVarInsn(Opcodes.ALOAD, 5);
                        pmv.visitInsn(Opcodes.ACONST_NULL);
                        pmv.visitInsn(Opcodes.ICONST_1);
                        pmv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
                        pmv.visitInsn(Opcodes.DUP);
                        pmv.visitInsn(Opcodes.ICONST_0);
                        pmv.visitVarInsn(Opcodes.ALOAD, 3);
                        pmv.visitInsn(Opcodes.AASTORE);
                        pmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitLabel(lTryEnd);

                        pmv.visitLabel(lCatchAll);
                        pmv.visitInsn(Opcodes.POP);
                        pmv.visitVarInsn(Opcodes.ALOAD, 0);
                        pmv.visitInsn(Opcodes.ARETURN);

                        pmv.visitMaxs(0, 0);
                        pmv.visitEnd();
                    }

                    super.visitEnd();
                }
            };

            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return modified[0] ? cw.toByteArray() : classfileBuffer;
        } catch (Throwable t) {
            System.err.println("[trvthnvker] Error transforming class " + className + ": " + t.getMessage());
            return classfileBuffer;
        }
    }

    private static boolean containsBytes(byte[] source, byte[] target) {
        if (source == null || target == null || source.length < target.length) {
            return false;
        }
        int max = source.length - target.length;
        for (int i = 0; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
