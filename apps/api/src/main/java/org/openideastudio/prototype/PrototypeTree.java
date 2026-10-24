package org.openideastudio.prototype;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Traversal helpers over the component tree. */
final class PrototypeTree {

    private PrototypeTree() {
    }

    static void walk(List<Component> components, int depth, ComponentVisitor visitor) {
        for (Component c : components) {
            visitor.visit(c, depth);
            walk(c.children(), depth + 1, visitor);
        }
    }

    interface ComponentVisitor {
        void visit(Component component, int depth);
    }

    static void actions(List<Component> components, Consumer<PrototypeAction> consumer) {
        walk(components, 0, (c, d) -> {
            switch (c) {
                case Component.Button b -> consumer.accept(b.action());
                case Component.Form f -> consumer.accept(f.action());
                case Component.Dialog dl -> consumer.accept(dl.action());
                case Component.ListView l -> l.items().stream()
                        .filter(i -> i.action() != null).forEach(i -> consumer.accept(i.action()));
                default -> {
                }
            }
        });
    }

    /** Returns a copy with every action rewritten; used to map model screen refs to stable IDs. */
    static List<Component> mapActions(List<Component> components, UnaryOperator<PrototypeAction> fn) {
        if (components == null) {
            return null;
        }
        List<Component> out = new ArrayList<>();
        for (Component c : components) {
            out.add(switch (c) {
                case Component.Button b -> new Component.Button(b.label(), b.variant(), fn.apply(b.action()));
                case Component.Form f -> new Component.Form(f.fields(), f.submitLabel(), fn.apply(f.action()));
                case Component.Dialog d -> new Component.Dialog(d.triggerLabel(), d.title(), d.body(),
                        d.confirmLabel(), fn.apply(d.action()));
                case Component.ListView l -> new Component.ListView(l.items().stream()
                        .map(i -> new Component.ListView.Item(i.title(), i.subtitle(), i.meta(), i.badge(),
                                i.action() == null ? null : fn.apply(i.action())))
                        .toList());
                case Component.Card card -> new Component.Card(card.title(), card.body(),
                        mapActions(card.children(), fn));
                case Component.Tabs t -> new Component.Tabs(t.tabs().stream()
                        .map(tab -> new Component.Tabs.Tab(tab.label(), mapActions(tab.children(), fn)))
                        .toList());
                case Component.Conditional cond -> new Component.Conditional(cond.key(), cond.equals(),
                        mapActions(cond.children(), fn));
                default -> c;
            });
        }
        return out;
    }
}
