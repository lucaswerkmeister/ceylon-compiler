/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.ceylondoc;

import static com.redhat.ceylon.ceylondoc.Util.getDoc;
import static com.redhat.ceylon.ceylondoc.Util.getModifiers;
import static com.redhat.ceylon.ceylondoc.Util.isAbbreviatedType;
import static com.redhat.ceylon.ceylondoc.Util.isEmpty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.runtime.CommonToken;

import com.redhat.ceylon.compiler.java.codegen.Decl;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Annotation;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public abstract class ClassOrPackageDoc extends CeylonDoc {
    
    private static final Set<String> simpleDefaultValues = new HashSet<String>();
    static {
        simpleDefaultValues.add("null");
        simpleDefaultValues.add("true");
        simpleDefaultValues.add("false");
        simpleDefaultValues.add("0");
        simpleDefaultValues.add("1");
        simpleDefaultValues.add("-1");
        simpleDefaultValues.add("0.0");
        simpleDefaultValues.add("1.0");
        simpleDefaultValues.add("-1.0");
        simpleDefaultValues.add("[]");
        simpleDefaultValues.add("\"\"");
    }
    
    public ClassOrPackageDoc(Module module, CeylonDocTool tool, Writer writer) {
		super(module, tool, writer);
	}
    
    protected final void doc(TypeAlias alias) throws IOException {
        open("tr");
        
        open("td id='" + alias.getName() + "' nowrap");
        writeIcon(alias);
        write(alias.getName());
        close("td");
        
        open("td");
        writeLinkOneSelf(alias);
        writeLinkSource(alias);
        writeTagged(alias);
        open("div class='signature'");
        around("span class='modifiers'", getModifiers(alias));
        write(" ");
        write(alias.getName());
        if (!alias.getTypeParameters().isEmpty()) {
            writeTypeParameters(alias.getTypeParameters());
            writeTypeParametersConstraints(alias.getTypeParameters());
            open("div class='type-alias-specifier'");
        }
        around("span class='specifier'", "=> ");
        linkRenderer().to(alias.getExtendedType()).write();
        if (!alias.getTypeParameters().isEmpty()) {
            close("div"); // type-alias-specifier
        }
        close("div"); // signature
        writeDescription(alias);
        close("td");
        
        close("tr");
    }

    protected final void doc(ClassOrInterface d) throws IOException {
        open("tr");
        
        open("td id='" + d.getName() + "' nowrap");
        writeIcon(d);
        around("a class='link-discreet' href='"+ linkRenderer().to(d).getUrl() +"'", d.getName());
        close("td");
        
        open("td");
        writeLinkOneSelf(d);
        writeLinkSourceCode(d);
        writeTagged(d);
        open("div class='signature'");
        around("span class='modifiers'", getModifiers(d));
        write(" ");
        linkRenderer().to(d.getType()).printAbbreviated(!isAbbreviatedType(d)).printTypeParameterDetail(true).write();
        close("div");
        writeDescription(d);
        close("td");
        
        close("tr");
    }

    protected final void doc(TypedDeclaration d) throws IOException {
        // put the id on the td because IE8 doesn't support id attributes on tr (yeah right)
        open("tr");
        
        open("td id='" + d.getName() + "' nowrap");
        writeIcon(d);
        write(d.getName());
        close("td");
        
        open("td");
        writeLinkOneSelf(d);
        writeLinkSource(d);
        writeTagged(d);
        
        if(d instanceof Functional)
            writeParameterLinksIfRequired((Functional) d);
        open("div class='signature'");
        around("span class='modifiers'", getModifiers(d));
        write(" ");
        
        if( d instanceof Functional && ((Functional) d).isDeclaredVoid() ) {
            around("span class='void'", "void");
        } else {
            linkRenderer().to(d.getType()).write();    
        }
        
        write(" ");
        write(d.getName());
        if( isConstantValue(d) ) {
            writeConstantValue((Value) d);
        }
        if( d instanceof Functional ) {
            Functional f = (Functional) d;
            writeTypeParameters(f.getTypeParameters());
            writeParameterList(f);
            writeTypeParametersConstraints(f.getTypeParameters());
        }
        if (d instanceof Value) {
            Setter setter = ((Value) d).getSetter();
            if (setter != null && Util.getAnnotation(setter.getAnnotations(), "doc") != null) {
                tool.warningSetterDoc(d.getQualifiedNameString(), d);
            }
        }
        close("div");
        writeDescription(d);
        close("td");
        close("tr");
    }
    
    private boolean isConstantValue(Declaration d) {
        if(Decl.isValue(d)) {
            Value value = (Value) d;
            if( value.isShared() && !value.isVariable() ) {
                Unit unit = value.getUnit();
                TypeDeclaration type = value.getTypeDeclaration();
                
                if (type == unit.getSequentialDeclaration()) {
                    ProducedType elementType = unit.getIteratedType(value.getType());
                    type = elementType.getDeclaration();
                }

                if (unit.getStringDeclaration().equals(type)
                        || unit.getIntegerDeclaration().equals(type)
                        || unit.getFloatDeclaration().equals(type)
                        || unit.getCharacterDeclaration().equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void writeConstantValue(Value v) throws IOException {
        Node node = tool.getNode(v);
        PhasedUnit pu = tool.getUnit(v);
        if (pu == null || !(node instanceof Tree.AttributeDeclaration)) {
            return;
        }
        
        Tree.AttributeDeclaration attribute = (Tree.AttributeDeclaration) node;
        Tree.SpecifierOrInitializerExpression specifierExpression = attribute.getSpecifierOrInitializerExpression();
        if (specifierExpression == null) {
            return;
        }
        
        String value = getSourceCode(pu, specifierExpression);
        int newLineIndex = value.indexOf("\n");
        String valueFirstLine = newLineIndex != -1 ? value.substring(0, newLineIndex) : value;

        around("span class='specifier'", valueFirstLine);
        if (newLineIndex != -1) {
            around("a class='specifier-ellipsis' href='#' title='Click for expand the rest of value.'", "...");
            open("div class='specifier-rest'");
            write(value.substring(newLineIndex + 1));
            close("div");
        }
    }

    private void writeDescription(Declaration d) throws IOException {
        open("div class='description'");
        writeDeprecated(d);
        String doc = getDoc(d, linkRenderer());
        if (isEmpty(doc)) {
            tool.warningMissingDoc(d.getQualifiedNameString(), d);
        }
        around("div class='doc section'", doc);
        if( d instanceof MethodOrValue ) {
            writeAnnotations(d);
        	writeParameters(d);
            writeThrows(d);        
            writeBy(d);
            writeSee(d);
            writeLinkToRefinedDeclaration(d);
        }
        if (d instanceof TypeAlias) {
            writeAnnotations(d);
            writeBy(d);
            writeSee(d);
        }
        close("div"); // description
    }
    
    private void writeLinkOneSelf(Declaration d) throws IOException {
        String url = linkRenderer().to(getFromObject()).useAnchor(d).getUrl();
        if (url != null) {
            open("a class='link-one-self' title='Link to this declaration' href='" + url + "'");
            write("<i class='icon-link'></i>");
            close("a");
        }
    }

    private void writeLinkSource(Declaration d) throws IOException {
        if (!tool.isIncludeSourceCode()) {
            return;
        }
        String srcUrl;
        if (d.isToplevel()) {
            srcUrl = linkRenderer().getSrcUrl(d);
        } else {
            srcUrl = linkRenderer().getSrcUrl(d.getContainer());
        }
        int[] lines = tool.getDeclarationSrcLocation(d);
        if(lines != null){
            open("a class='link-source-code' title='Link to source code' href='" + srcUrl + "#" + lines[0] + "," + lines[1] + "'");
            write("<i class='icon-source-code'></i>");
            write("Source Code");
            close("a");
        }
    }

    private void writeLinkToRefinedDeclaration(Declaration d) throws IOException {
        Declaration refinedDecl = d.getRefinedDeclaration();
        if (refinedDecl != null && refinedDecl != d) {
            open("div class='refined section'");
            around("span class='title'", "Refined declaration: ");
            linkRenderer().to(refinedDecl).write();
            open("span class='value'");
            close("span");
            close("div");
        }
    }

    protected final void writeTypeParameters(List<TypeParameter> typeParameters) throws IOException {
        if (typeParameters != null && !typeParameters.isEmpty()) {
            write("<span class='type-parameter'>");
            write("&lt;");
            boolean first = true;
            for (TypeParameter typeParam : typeParameters) {
                if (first) {
                    first = false;
                } else {
                    write(", ");
                }
                if (typeParam.isContravariant()) {
                    write("<span class='type-parameter-keyword'>in </span>");
                }
                if (typeParam.isCovariant()) {
                    write("<span class='type-parameter-keyword'>out </span>");
                }
                write(typeParam.getName());
                if (typeParam.isDefaulted() && typeParam.getDefaultTypeArgument() != null){
                    write("<span class='type-parameter-keyword'> = </span>");
                    write(linkRenderer().to(typeParam.getDefaultTypeArgument()).getLink());
                }
            }
            write("&gt;");
            write("</span>");
        }
    }
    
    protected final void writeTypeParametersConstraints(List<TypeParameter> typeParameters) throws IOException {
        for (TypeParameter typeParam : typeParameters) {
            if (typeParam.isConstrained()) {
                open("div class='type-parameter-constraint'");

                write("<span class='type-parameter-keyword'>given </span>");
                write("<span class='type-parameter'>", typeParam.getName(), "</span>");

                List<ProducedType> satisfiedTypes = typeParam.getSatisfiedTypes();
                if (satisfiedTypes != null && !satisfiedTypes.isEmpty()) {
                    write("<span class='type-parameter-keyword'> satisfies </span>");
                    boolean first = true;
                    for (ProducedType satisfiedType : satisfiedTypes) {
                        if (first) {
                            first = false;
                        } else {
                            write(" &amp; ");
                        }
                        write(linkRenderer().to(satisfiedType).getLink());
                    }
                }

                List<ProducedType> caseTypes = typeParam.getCaseTypes();
                if (caseTypes != null && !caseTypes.isEmpty()) {
                    write("<span class='type-parameter-keyword'> of </span>");
                    boolean first = true;
                    for (ProducedType caseType : caseTypes) {
                        if (first) {
                            first = false;
                        } else {
                            write(" | ");
                        }
                        write(linkRenderer().to(caseType).getLink());
                    }
                }

                if (typeParam.getParameterList() != null) {
                    writeParameterList(typeParam);
                }

                close("div");
            }
        }
    }

    protected final void writeParameterLinksIfRequired(Functional f) throws IOException {
        // check if any parameter has documentation
        boolean hasDoc = false;
        for (ParameterList lists : f.getParameterLists()) {
            for (Parameter param : lists.getParameters()) {
                if(!hasDoc){
                    String doc = getDoc(param.getModel(), linkRenderer());
                    hasDoc = !doc.isEmpty();
                }
            }
        }
        // if no parameter has any documentation, we need to produce url anchors for them
        // because writeParameters() will not produce a parameter list with url anchors
        for (ParameterList lists : f.getParameterLists()) {
            for (Parameter param : lists.getParameters()) {
                around("a id='" + f.getName() + "-" + param.getName() + "'", "");
            }
        }
    }

    protected final void writeParameterList(Functional f) throws IOException {
        for (ParameterList lists : f.getParameterLists()) {
            write("(");
            boolean first = true;
            for (Parameter param : lists.getParameters()) {
                if (!first) {
                    write(", ");
                } else {
                    first = false;
                }
                
                if (param.getModel() instanceof Method) {
                    writeFunctionalParameter(param);
                } else {
                    linkRenderer().to(param.getType()).write();
                    write(" ", param.getName());
                }
                
                if (param.isDefaulted()) {
                    String defaultValue = getParameterDefaultValue(param);
                    if (defaultValue != null) {
                        around("span class='parameter-default-value'", " = ");
                        if (simpleDefaultValues.contains(defaultValue)) {
                            around("span class='parameter-default-value' title='Parameter default value'", defaultValue);
                        } else {
                            around("a class='parameter-default-value' href='#" + f.getName() + "-" + param.getName() + "' title='Go to parameter default value'", "...");
                        }
                    }
                }
                
            }
            write(")");
        }
    }
    
    private String getParameterDefaultValue(Parameter param) throws IOException {
        String defaultValue = null;
        
        if( param.isDefaulted() ) {
            PhasedUnit pu = tool.getParameterUnit(param);
            Node paramNode = tool.getParameterNode(param);
            if (pu != null && paramNode instanceof Tree.Parameter) {
                Tree.SpecifierOrInitializerExpression defArg = getDefaultArgument((Tree.Parameter) paramNode);
                if (defArg != null) {
                    defaultValue = getSourceCode(pu, defArg.getExpression());
                    if (defaultValue != null) {
                        defaultValue = defaultValue.trim();
                    }
                }
            }
        }
        
        return defaultValue;
    }
    
    private Tree.SpecifierOrInitializerExpression getDefaultArgument(Tree.Parameter parameter) {
        if (parameter instanceof Tree.InitializerParameter) {
            return ((Tree.InitializerParameter)parameter).getSpecifierExpression();
        } else if (parameter instanceof Tree.ValueParameterDeclaration) {
            return ((Tree.AttributeDeclaration)((Tree.ValueParameterDeclaration)parameter).getTypedDeclaration()).getSpecifierOrInitializerExpression();
        } else if (parameter instanceof Tree.FunctionalParameterDeclaration) {
            return ((Tree.MethodDeclaration)((Tree.FunctionalParameterDeclaration)parameter).getTypedDeclaration()).getSpecifierExpression();
        }
        return null;
    }

    private void writeFunctionalParameter(Parameter functionParam) throws IOException {
        if( functionParam.isDeclaredVoid() ) {
            around("span class='void'", "void");
        } else {
            linkRenderer().to(functionParam.getType()).write();
        }
        write(" ");
        write(functionParam.getName());
        writeParameterList((Method)functionParam.getModel());
    }

    protected final void writeParameters(Declaration decl) throws IOException {
        if( decl instanceof Functional ) {
            Map<Parameter, Map<Tree.Assertion, List<Tree.Condition>>> parametersAssertions = getParametersAssertions(decl);
            boolean first = true;
            List<ParameterList> parameterLists = ((Functional)decl).getParameterLists();
            for (ParameterList parameterList : parameterLists) {
                for (Parameter parameter : parameterList.getParameters()) {

                    String doc = getDoc(parameter.getModel(), linkRenderer());
                    String defaultValue = getParameterDefaultValue(parameter);
                    Map<Tree.Assertion, List<Tree.Condition>> parameterAssertions = parametersAssertions.get(parameter);
                    
                    if( !doc.isEmpty() || defaultValue != null || parameterAssertions != null) {
                        if( first ) {
                            first = false;
                            open("div class='parameters section'");
                            around("span class='title'", "Parameters: ");
                            open("ul");
                        }
                        open("li");
                        around("span class='parameter' id='" + decl.getName() + "-" + parameter.getName() + "'", parameter.getName());
                        
                        if (!isEmpty(defaultValue)) {
                            around("span class='parameter-default-value' title='Parameter default value'", " = " + defaultValue);
                        }
                        if (!isEmpty(doc)) {
                            around("div class='doc section'", doc);
                        }
                        writeParameterAssertions(decl, parameterAssertions);
                        
                        close("li");
                    }
                }
            }    			
            if (!first) {
                close("ul");
                close("div");
            }
        }
    }

    private void writeParameterAssertions(Declaration decl, Map<Tree.Assertion, List<Tree.Condition>> parameterAssertions) throws IOException {
        if (parameterAssertions == null || parameterAssertions.isEmpty()) {
            return;
        }
        
        PhasedUnit pu = tool.getUnit(decl);
        
        open("div class='assertions' title='Parameter assertions'");
        open("ul");

        for (Tree.Assertion assertion : parameterAssertions.keySet()) {

            List<Annotation> annotations = new ArrayList<Annotation>();
            com.redhat.ceylon.compiler.typechecker.analyzer.Util.buildAnnotations(assertion.getAnnotationList(), annotations);

            String doc = Util.getRawDoc(annotations);
            if (!Util.isEmpty(doc)) {
                open("li");
                write("<i class='icon-assertion'></i>");
                write(Util.wikiToHTML(doc, linkRenderer()));
                close("li");
            } else {
                for (Tree.Condition c : parameterAssertions.get(assertion)) {
                    String sourceCode = getSourceCode(pu, c);
                    open("li");
                    write("<i class='icon-assertion'></i>");
                    around("code", sourceCode);
                    close("li");
                }
            }
        }

        close("ul");
        close("div");
    }

	protected final void writeThrows(Declaration decl) throws IOException {
        boolean first = true;
        for (Annotation annotation : decl.getAnnotations()) {
            if (annotation.getName().equals("throws")) {

                String excType = annotation.getPositionalArguments().get(0);
                String excDesc = annotation.getPositionalArguments().size() == 2 ? annotation.getPositionalArguments().get(1) : null;
                
                if (first) {
                    first = false;
                    open("div class='throws section'");
                    around("span class='title'", "Throws: ");
                    open("ul");
                }

                open("li");
                
                linkRenderer().to(excType).useScope(decl).write();

                if (excDesc != null) {
                    write(Util.wikiToHTML(excDesc, linkRenderer().useScope(decl)));
                }

                close("li");
            }
        }
        if (!first) {
            close("ul");
            close("div");
        }
        tool.warningMissingThrows(decl);
    }
    
    private void writeDeprecated(Declaration decl) throws IOException {
        Annotation deprecated = Util.findAnnotation(decl, "deprecated");
        if (deprecated != null) {
            open("div class='deprecated section'");
            String text = "<span class='title'>Deprecated: </span>";
            if (!deprecated.getPositionalArguments().isEmpty()) {
                String reason = deprecated.getPositionalArguments().get(0);
                if (reason != null) {
                    text += reason;
                }
            }
            write(Util.wikiToHTML(text, linkRenderer().useScope(decl)));
            close("div");
        }
    }
    
    protected final void writeSee(Declaration decl) throws IOException {
        Annotation see = Util.getAnnotation(decl.getAnnotations(), "see");
        if(see == null)
            return;

        open("div class='see section'");
        around("span class='title'", "See also: ");
        
        open("span class='value'");
        boolean first = true;
        for (String target : see.getPositionalArguments()) {
            if (!first) {
                write(", ");
            } else {
                first = false;
            }
            linkRenderer().to(target).useScope(decl).printAbbreviated(false).printTypeParameters(false).write();
        }
        close("span");
        
        close("div");
    }

    protected final void writeTagged(Declaration decl) throws IOException {
        List<String> tags = Util.getTags(decl);
        if (!tags.isEmpty()) {
            open("div class='tags section'");
            Iterator<String> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                String tag = tagIterator.next();
                write("<a class='tag label' name='" + tag + "' href='search.html?q=" + tag + "'>" + tag + "</a>");
            }
            close("div");
        }
    }

    private String getSourceCode(PhasedUnit pu, Node node) throws IOException {
        int startIndex = ((CommonToken) node.getToken()).getStartIndex();
        int stopIndex = ((CommonToken) node.getEndToken()).getStopIndex();
    
        StringBuilder sourceCodeBuilder = new StringBuilder();
        BufferedReader sourceCodeReader = new BufferedReader(new InputStreamReader(pu.getUnitFile().getInputStream()));
        try {
            while (true) {
                int c = sourceCodeReader.read();
                if (c == -1 || sourceCodeBuilder.length() > stopIndex) {
                    break;
                }
                sourceCodeBuilder.append((char) c);
            }
        } finally {
            sourceCodeReader.close();
        }

        String sourceCode = sourceCodeBuilder.substring(startIndex, stopIndex + 1);
        sourceCode = sourceCode.replaceAll("&", "&amp;");
        sourceCode = sourceCode.replaceAll("<", "&lt;");
        sourceCode = sourceCode.replaceAll(">", "&gt;");
        return sourceCode;
    }
    
    private Map<Parameter, Map<Tree.Assertion, List<Tree.Condition>>> getParametersAssertions(final Declaration decl) {
        final Map<Parameter, Map<Tree.Assertion, List<Tree.Condition>>> parametersAssertions = new LinkedHashMap<Parameter, Map<Tree.Assertion, List<Tree.Condition>>>();
        
        if (((Functional) decl).getParameterLists().isEmpty()) {
            return parametersAssertions;
        }

        Node node = tool.getNode(decl);
        PhasedUnit pu = tool.getUnit(decl);
        if (node == null || pu == null) {
            return parametersAssertions;
        }

        Tree.Body body = null;
        if (node instanceof Tree.MethodDefinition) {
            body = ((Tree.MethodDefinition) node).getBlock();
        } else if (node instanceof Tree.ClassDefinition) {
            body = ((Tree.ClassDefinition) node).getClassBody();
        }

        if (body == null) {
            return parametersAssertions;
        }

        final Map<String, Parameter> parametersNames = new HashMap<String, Parameter>();
        for (ParameterList parameterList : ((Functional) decl).getParameterLists()) {
            for (Parameter parameter : parameterList.getParameters()) {
                parametersNames.put(parameter.getName(), parameter);
            }
        }

        body.visitChildren(new Visitor() {

            private boolean stop = false;
            private Tree.Assertion assertion = null;
            private Set<Parameter> referencedParameters = new HashSet<Parameter>();

            @Override
            public void visit(Tree.Assertion that) {
                assertion = that;
                super.visit(that);
                assertion = null;
            }

            @Override
            public void visit(Tree.Condition that) {
                referencedParameters.clear();
                super.visit(that);
                if (assertion != null && !referencedParameters.isEmpty()) {
                    for (Parameter referencedParameter : referencedParameters) {
                        Map<Tree.Assertion, List<Tree.Condition>> parameterAssertions = parametersAssertions.get(referencedParameter);
                        if (parameterAssertions == null) {
                            parameterAssertions = new LinkedHashMap<Tree.Assertion, List<Tree.Condition>>();
                            parametersAssertions.put(referencedParameter, parameterAssertions);
                        }

                        List<Tree.Condition> parameterConditions = parameterAssertions.get(assertion);
                        if (parameterConditions == null) {
                            parameterConditions = new ArrayList<Tree.Condition>();
                            parameterAssertions.put(assertion, parameterConditions);
                        }

                        parameterConditions.add(that);
                    }
                }
            }

            @Override
            public void visit(Tree.BaseMemberExpression that) {
                if (assertion != null) {
                    Declaration d = that.getDeclaration();
                    Scope realScope = com.redhat.ceylon.compiler.typechecker.model.Util.getRealScope(d.getScope());
                    if (parametersNames.containsKey(d.getName()) && realScope == decl) {
                        referencedParameters.add(parametersNames.get(d.getName()));
                    }
                }
                super.visit(that);
            }

            @Override
            public void visit(Tree.Statement that) {
                if (assertion == null) {
                    stop = true;
                }
                super.visit(that);
            }

            @Override
            public void visitAny(Node that) {
                if (!stop) {
                    super.visitAny(that);
                }
            }

        });

        return parametersAssertions;
    }

}