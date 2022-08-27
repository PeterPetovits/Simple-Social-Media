package socialNetwork;

/*
 * The class Pair is used to store data together as a set/pair. 
 * It has two variables, one key and one value, which contains the data of that specific key.
 */

public class Pair<K,V> {
	private K key;
	private V value;
	
	//Constructor for the class Pair
	public Pair(K key, V value) {
		this.key = key;
		this.value = value;
	}

	//returns the key
	public K getKey() {
		return key;
	}

	//sets the key
	public void setKey(K key) {
		this.key = key;
	}

	//returns the value
	public V getValue() {
		return value;
	}

	//sets the value
	public void setValue(V value) {
		this.value = value;
	}
	
	//custom equals method used to check if an Object is instance of the class Pair.
	//if it is, we return true. If it is not, we return false. Otherwise it returns a key and a value that match
	@Override
	public boolean equals(Object o) {
		
		if (o == this) {
            return true;
        }
		
		 if (!(o instanceof Pair)) {
			 return false;
	     }
		 
		 Pair<K,V> p = (Pair<K,V>)o;
		 return this.key.equals(p.key) && this.value.equals(p.value);
	}
	
}
