package miniJava.CodeGenerator;

public class RuntimeEntity {
	public int address;
	
	public RuntimeEntity(int _address) {
		this.address = _address;
	}

	public void patchAddr(int newAddr) {
		this.address = newAddr;
	}
}
