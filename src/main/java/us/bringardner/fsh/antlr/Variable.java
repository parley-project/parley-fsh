package us.bringardner.fsh.antlr;

public class Variable {
	private String name;
	private Object value;
	private boolean exported = false;
	
	
	public Variable(String name) {
		this(name, null);
	}
	
	public Variable(String name,Object value) {
		this(name, value, false);
	}

	public Variable(String name,Object value,boolean exported) {
		this.name = name;
		this.value = value;
		this.exported = exported;
	}

	@Override
	public String toString() {
		return name;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Variable) {
			Variable v = (Variable) obj;
			return  Statement.testEq(name, v.name) && Statement.testEq(value, v.value);
		}
		
		return false;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public boolean isExported() {
		return exported;
	}

	public void setExported(boolean exported) {
		this.exported = exported;
	}
	
	
}
