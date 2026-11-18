package com.uky.corpses.port.physics;

@FunctionalInterface
public interface ContactVisitor {

    void visit(ContactPair var1);
}
