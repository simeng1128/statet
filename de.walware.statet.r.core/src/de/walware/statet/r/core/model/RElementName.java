/*******************************************************************************
 * Copyright (c) 2008-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.core.model;

import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_TOKEN_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS_MASK_12;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import com.ibm.icu.text.Collator;

import de.walware.ecommons.ltk.IElementName;
import de.walware.ecommons.text.SourceParseInput;
import de.walware.ecommons.text.StringParseInput;

import de.walware.statet.r.core.RSymbolComparator;
import de.walware.statet.r.core.rlang.RTerminal;
import de.walware.statet.r.core.rsource.RLexer;


/**
 * Base class for R element names
 * 
 * Defines type constants and provides static utility methods.
 */
public abstract class RElementName implements IElementName {
	
	
	public static final int RESOURCE =        0x011;
	
	public static final int MAIN_OTHER =      0x020;
	public static final int MAIN_DEFAULT =    0x021;
	public static final int MAIN_CLASS =      0x023;
	public static final int MAIN_SLOT =       0x024;
	public static final int MAIN_SEARCH_ENV = 0x025;
	public static final int MAIN_PACKAGE =    0x026;
	public static final int MAIN_PROJECT =    0x027;
	public static final int SUB_NAMEDSLOT =   0x028;
	public static final int SUB_NAMEDPART =   0x029;
	public static final int SUB_INDEXED_S =   0x02A;
	public static final int SUB_INDEXED_D =   0x02B;
	
	public static final int DISPLAY_NS_PREFIX = 1;
	
	
	public static String createDisplayName(IElementName a, final int options) {
		StringBuilder sb = null;
		
		final IElementName namespace = ((options & DISPLAY_NS_PREFIX) != 0) ? a.getNamespace() : null;
		if (((options & DISPLAY_NS_PREFIX) != 0) && namespace != null) {
			final String namespaceName;
			switch (namespace.getType()) {
			case MAIN_SEARCH_ENV:
				namespaceName = namespace.getSegmentName();
				break;
			case MAIN_PACKAGE:
				namespaceName = "package:"+namespace.getSegmentName();
				break;
			case MAIN_PROJECT:
				namespaceName = ".GlobalEnv";
				break;
			default:
				return null;
			}
			if (namespaceName != null) {
				sb = new StringBuilder(namespaceName.length() + 32);
				sb.append("as.environment(\""); //$NON-NLS-1$
				sb.append(namespaceName);
				sb.append("\")"); //$NON-NLS-1$
			}
			else {
				return null;
			}
			if (a != null && a.getType() == MAIN_DEFAULT) {
				sb.append('$');
				final String name = a.getSegmentName();
				if (name != null) {
					appendSymbol(sb, name);
				}
				a = a.getNextSegment();
			}
			else {
				return null;
			}
		}
		else if (((options & DISPLAY_NS_PREFIX) != 0)
				&& (a.getType() == MAIN_SEARCH_ENV || a.getType() == MAIN_PACKAGE)) {
			final String namespaceName;
			switch (a.getType()) {
			case MAIN_SEARCH_ENV:
				namespaceName = a.getSegmentName();
				break;
			case MAIN_PACKAGE:
				namespaceName = "package:"+a.getSegmentName();
				break;
			case MAIN_PROJECT:
				namespaceName = ".GlobalEnv";
				break;
			default:
				return null;
			}
			if (namespaceName != null) {
				sb = new StringBuilder(namespaceName.length() + 32);
				sb.append("as.environment(\""); //$NON-NLS-1$
				sb.append(namespaceName);
				sb.append("\")"); //$NON-NLS-1$
			}
			else {
				return null;
			}
			a = a.getNextSegment();
			if (a != null && a.getType() == MAIN_DEFAULT) {
				sb.append('$');
				final String name = a.getSegmentName();
				if (name != null) {
					appendSymbol(sb, name);
				}
				a = a.getNextSegment();
			}
			else if (a == null) {
				return sb.toString();
			}
			else {
				return null;
			}
		}
		else {
			String firstName;
			final int type = a.getType();
			switch (type) {
			case MAIN_DEFAULT:
			case MAIN_CLASS:
			case MAIN_SLOT:
			case SUB_NAMEDPART:
			case SUB_NAMEDSLOT:
				firstName = a.getSegmentName();
				if (firstName != null) {
					sb = appendSymbol(sb, firstName);
				}
				a = a.getNextSegment();
				if (a == null) {
					return (sb != null) ? sb.toString() : firstName;
				}
				if (sb == null) {
					sb = (firstName != null) ? new StringBuilder(firstName) : new StringBuilder();
				}
				break;
			case MAIN_PACKAGE:
				firstName = a.getSegmentName();
				if (firstName != null) {
					return "package:"+firstName;
				}
			case MAIN_PROJECT:
				firstName = a.getSegmentName();
				if (firstName != null) {
					return "project:"+firstName;
				}
			case MAIN_SEARCH_ENV:
				firstName = a.getSegmentName();
				if (firstName != null) {
					return firstName;
				}
				return null;
			case SUB_INDEXED_D:
				if (a instanceof DefaultImpl) {
					sb = new StringBuilder("[["); //$NON-NLS-1$
					sb.append(a.getSegmentName());
					sb.append("]]"); //$NON-NLS-1$
					a = a.getNextSegment();
					break;
				}
				return null;
			case RESOURCE:
			case MAIN_OTHER:
				return a.getSegmentName();
			default:
				return null;
			}
		}
		
		APPEND_SUB : while (a != null) {
			String name;
			switch (a.getType()) {
			case MAIN_DEFAULT:
			case MAIN_CLASS:
			case MAIN_SLOT:
				name = a.getSegmentName();
				if (name != null) {
					appendSymbol(sb, name);
				}
				a = a.getNextSegment();
				continue APPEND_SUB;
			case SUB_NAMEDPART:
				sb.append('$');
				name = a.getSegmentName();
				if (name != null) {
					appendSymbol(sb, name);
				}
				a = a.getNextSegment();
				continue APPEND_SUB;
			case SUB_NAMEDSLOT:
				sb.append('@');
				name = a.getSegmentName();
				if (name != null) {
					appendSymbol(sb, name);
				}
				a = a.getNextSegment();
				continue APPEND_SUB;
			case SUB_INDEXED_S:
				sb.append("[…]"); //$NON-NLS-1$
				break APPEND_SUB;
			case SUB_INDEXED_D:
				if (a instanceof DefaultImpl) {
					sb.append("[["); //$NON-NLS-1$
					sb.append(a.getSegmentName());
					sb.append("]]"); //$NON-NLS-1$
					a = a.getNextSegment();
					continue APPEND_SUB;
				}
				sb.append("[[…]]"); //$NON-NLS-1$
				break APPEND_SUB;
			default:
				sb.append(" …"); //$NON-NLS-1$
				break APPEND_SUB;
			}
		}
		return sb.toString();
	}
	
	private static StringBuilder appendSymbol(StringBuilder sb, final String name) {
		if (name == null) {
			return null;
		}
		final int l = name.length();
		if (l == 0) {
			return (sb != null) ? sb.append("``") : new StringBuilder("``"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		final char c0 = name.charAt(0);
		int check;
		if (Character.isLetter(c0)) {
			check = 1;
		}
		else if (c0 == '.') {
			if (l == 1) {
				check = 1;
			}
			else {
				final char c1 = name.charAt(1);
				if (c1 == '.' || c1 == '_' || Character.isLetter(c1)) {
					check = 2;
				}
				else {
					check = -1;
				}
			}
		}
		else {
			check = -1;
		}
		VALID : if (check >= 0) {
			for (; check < l; check++) {
				final char cn = name.charAt(check);
				if ((cn < 'a' || cn > 'z') && cn != '.' && cn != '_'  && !Character.isLetterOrDigit(cn)) {
					break VALID;
				}
			}
			return (sb != null) ? sb.append(name) : null;
		}
		// no valid
		if (sb == null) {
			sb = new StringBuilder(name.length()+18);
		}
		sb.append('`');
		sb.append(name);
		sb.append('`');
		return sb;
	}
	
	private static final Collator NAME_COLLATOR = RSymbolComparator.R_NAMES_COLLATOR;
	
	public static final Comparator<IElementName> NAMEONLY_COMPARATOR = new Comparator<IElementName>() {
		
		public int compare(IElementName o1, IElementName o2) {
			final String n1 = o1.getSegmentName();
			final String n2 = o2.getSegmentName();
			if (n1 != null) {
				if (n2 != null) {
					final int diff = NAME_COLLATOR.compare(n1, n2);
					if (diff != 0) {
						return diff;
					}
				}
				else {
					return Integer.MIN_VALUE;
				}
			}
			else if (n2 != null) {
				return Integer.MAX_VALUE;
			}
			
			o1 = o1.getNextSegment();
			o2 = o2.getNextSegment();
			if (o1 != null) {
				if (o2 != null) {
					final int diff = o1.getType() - o2.getType();
					if (diff != 0) {
						return diff;
					}
					return compare(o1, o2);
				}
				else {
					return Integer.MIN_VALUE+100;
				}
			}
			else if (n2 != null) {
				return Integer.MAX_VALUE-100;
			}
			return 0;
		}
	};
	
	
	private static class DefaultImpl extends RElementName implements Serializable {
		
		
		private static final long serialVersionUID = 315497720879434929L;
		
		
		private final int fType;
		private final String fSegmentName;
		private IElementName fNamespace;
		private IElementName fNextSegment;
		
		
		public DefaultImpl(final int type, final String segmentName) {
			fType = type;
			fSegmentName = segmentName;
			fNextSegment = null;
		}
		
		public DefaultImpl(final int type, final IElementName namespace, final String segmentName, final IElementName next) {
			fType = type;
			fSegmentName = segmentName;
			fNamespace = namespace;
			fNextSegment = next;
		}
		
		public DefaultImpl(final int type, final String segmentName, final IElementName next) {
			fType = type;
			fSegmentName = segmentName;
			fNextSegment = next;
		}
		
		
		public int getType() {
			return fType;
		}
		
		public String getSegmentName() {
			return fSegmentName;
		}
		
		public IElementName getNamespace() {
			return fNamespace;
		}
		
		public IElementName getNextSegment() {
			return fNextSegment;
		}
		
	}
	
	/**
	 * Lexer for RScanner.
	 */
	private static class ParseLexer extends RLexer {
		
		
		RTerminal type;
		String text;
		
		
		public ParseLexer(final SourceParseInput input) {
			super(input);
		}
		
		public void nextToken() {
			do {
				searchNext();
			} while (this.type == null);
		}
		
		
		@Override
		protected void createFix(final RTerminal type) {
			this.type = type;
			this.text = null;
		}
		
		@Override
		protected void createSpecialToken(final int status) {
			this.type = RTerminal.SPECIAL;
			this.text = null;
		}
		
		@Override
		protected void createSymbolToken() {
			this.type = RTerminal.SYMBOL;
			this.text = fInput.substring(1, fNextNum);
		}
		
		@Override
		protected void createQuotedSymbolToken(final RTerminal type, final int status) {
			this.type = type;
			this.text = ((status & STATUS_MASK_12) != STATUS2_SYNTAX_TOKEN_NOT_CLOSED) ?
					fInput.substring(2, fNextNum-2) : fInput.substring(2, fNextNum-1);
		}
		
		@Override
		protected void createStringToken(final RTerminal type, final int status) {
			this.type = type;
			this.text = ((status & STATUS_MASK_12) != STATUS2_SYNTAX_TOKEN_NOT_CLOSED) ?
					fInput.substring(2, fNextNum-2) : fInput.substring(2, fNextNum-1);
		}
		
		@Override
		protected void createNumberToken(final RTerminal type, final int status) {
			this.type = type;
			this.text = fInput.substring(1, fNextNum);
		}
		
		@Override
		protected void createWhitespaceToken() {
			this.type = null;
		}
		
		@Override
		protected void createCommentToken(final RTerminal type) {
			this.type = null;
		}
		
		@Override
		protected void createLinebreakToken(final String text) {
			this.type = null;
		}
		
		@Override
		protected void createUnknownToken(final String text) {
			this.type = RTerminal.UNKNOWN;
			this.text = text;
		}
		
	}
	
	public static RElementName create(final int type, final String segmentName) {
		return new DefaultImpl(type, segmentName);
	}
	
	public static RElementName parseDefault(final String code) {
		final ParseLexer lexer = new ParseLexer(new StringParseInput(code));
		lexer.setFull();
		
		int mode = MAIN_DEFAULT;
		DefaultImpl main = null;
		DefaultImpl last = null;
		while (true) {
			DefaultImpl tmp = null;
			lexer.nextToken();
			if (lexer.type == null || lexer.type == RTerminal.EOF) {
				if (mode >= 0) {
					tmp = new DefaultImpl(mode, ""); //$NON-NLS-1$
					if (main == null) {
						main = last = tmp;
					}
					else {
						last.fNextSegment = tmp;
						last = tmp;
					}
				}
				return main;
			}
			else {
				switch(lexer.type) {
				case IF:
				case ELSE:
				case FOR:
				case IN:
				case WHILE:
				case REPEAT:
				case NEXT:
				case BREAK:
				case FUNCTION:
				case TRUE:
				case FALSE:
				case NA:
				case NA_INT:
				case NA_REAL:
				case NA_CPLX:
				case NA_CHAR:
				case NULL:
				case NAN:
				case INF:
					if (mode != MAIN_DEFAULT && mode != MAIN_PACKAGE
							&& mode != SUB_NAMEDPART && mode != SUB_NAMEDSLOT) {
						return null;
					}
					tmp = new DefaultImpl(mode, lexer.type.text);
					if (main == null) {
						main = last = tmp;
					}
					else {
						last.fNextSegment = tmp;
						last = tmp;
					}
					lexer.nextToken();
					if (lexer.type == null || lexer.type == RTerminal.EOF) {
						return main; // valid prefix
					}
					else {
						return null; // invalid
					}
				case SYMBOL:
				case SYMBOL_G:
					if (mode != MAIN_DEFAULT && mode != MAIN_PACKAGE
							&& mode != SUB_NAMEDPART && mode != SUB_NAMEDSLOT) {
						return null;
					}
					tmp = new DefaultImpl(mode, lexer.text);
					if (main == null) {
						main = last = tmp;
					}
					else {
						last.fNextSegment = tmp;
						last = tmp;
					}
					mode = -1;
					continue;
				case NUM_INT:
				case NUM_NUM:
					if (mode != SUB_INDEXED_S && mode != SUB_INDEXED_D) {
						return null;
					}
					tmp = new DefaultImpl(mode, lexer.text);
					lexer.nextToken();
					if (lexer.type != RTerminal.SUB_INDEXED_CLOSE) {
						return null;
					}
					if (main == null) {
						main = last = tmp;
					}
					else {
						last.fNextSegment = tmp;
						last = tmp;
					}
					mode = -2;
					continue;
				case SUB_NAMED_PART:
					if (main == null || mode >= 0) {
						return null;
					}
					mode = SUB_NAMEDPART;
					continue;
				case SUB_NAMED_SLOT:
					if (main == null || mode >= 0) {
						return null;
					}
					mode = SUB_NAMEDSLOT;
					continue;
				case SUB_INDEXED_S_OPEN:
					if (main == null || mode >= 0) {
						return null;
					}
					mode = SUB_INDEXED_S;
					continue;
				case SUB_INDEXED_D_OPEN:
					if (main == null || mode >= 0) {
						return null;
					}
					mode = SUB_INDEXED_S;
					continue;
				case SUB_INDEXED_CLOSE:
					if (mode != -2) {
						return null;
					}
					continue;
				case NS_GET:
				case NS_GET_INT:
					if (main != null || mode >= 0) {
						return null;
					}
					mode = MAIN_PACKAGE;
					continue;
				default:
					return null;
				}
			}
		}
	}
	
	public static IElementName cloneSegment(final IElementName segment) {
		return new DefaultImpl(segment.getType(), segment.getSegmentName(), null);
	}
	
	public static IElementName cloneName(IElementName name, final boolean withNamespace) {
		if (name == null) {
			return null;
		}
		IElementName namespace = (withNamespace) ? name.getNamespace() : null;
		if (namespace != null) {
			namespace = cloneSegment(namespace);
		}
		final DefaultImpl main = new DefaultImpl(name.getType(), namespace, name.getSegmentName(), null);
		DefaultImpl last = main;
		name = name.getNextSegment();
		while (name != null) {
			final DefaultImpl copy = new DefaultImpl(name.getType(), name.getSegmentName());
			last.fNextSegment = copy;
			last = copy;
			name = name.getNextSegment();
		}
		return main;
	}
	
	public static IElementName concat(final List<IElementName> segments) {
		if (segments.size() > 0) {
			int first = 0;
			IElementName namespace = segments.get(first);
			if (namespace.getType() == MAIN_SEARCH_ENV || namespace.getType() == MAIN_PACKAGE) {
				first++;
			}
			else {
				namespace = null;
			}
			if (segments.size() > first) {
				IElementName next = null;
				for (int i = segments.size()-1; i > first; i--) {
					next = new DefaultImpl(segments.get(i).getType(), segments.get(i).getSegmentName(), next);
				}
				next = new DefaultImpl(segments.get(first).getType(), namespace, segments.get(first).getSegmentName(), next);
				return next;
			}
		}
		return null;
	}
	
	
	public String getDisplayName() {
		return createDisplayName(this, 0);
	}
	
	
	@Override
	public final int hashCode() {
		final String name = getSegmentName();
		final IElementName next = getNextSegment();
		return getType() * ((name != null) ? name.hashCode() : 1) * ((next != null) ? next.hashCode() : 1);
	}
	
	@Override
	public final boolean equals(final Object obj) {
		if (!(obj instanceof RElementName)) {
			return false;
		}
		final IElementName other = (RElementName) obj;
		final String thisName = getSegmentName();
		final String otherName = other.getSegmentName();
		return ((getType() == other.getType())
				&& ((thisName != null) ? 
						(thisName == otherName || (otherName != null && thisName.hashCode() == otherName.hashCode() && thisName.equals(otherName)) ) : 
						(other.getSegmentName() == null) )
				&& ((getNextSegment() != null) ? 
						(getNextSegment().equals(other.getNextSegment())) :
						(other.getNextSegment() == null) ) );
	}
	
	@Override
	public String toString() {
		return getDisplayName();
	}
	
}
