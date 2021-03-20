package tardis.implementation;

/**
 * Class that defines the ({@link BloomFilter}, label)
 * pair structure used in the training set. Used to
 * calculate the infeasibility index.
 */

final class TrainingItem {
    private final BloomFilter bloomFilter;
    private final boolean label;
    private final int hashCode;

    /**
     * Constructor
     * 
     * @param bloomFilter a {@link BloomFilter}. 
     * @param label a {@code boolean}.
     */
    TrainingItem(BloomFilter bloomFilter, boolean label) {
        this.bloomFilter = bloomFilter;
        this.label = label;
        
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.bloomFilter == null ? 0 : this.bloomFilter.hashCode());
        result = prime * result + (this.label ? 1 : 0);
        this.hashCode = result;
    }

    BloomFilter getBloomFilter(){
        return this.bloomFilter;
    }

    boolean getLabel() {
        return this.label;
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TrainingItem other = (TrainingItem) obj;
        if (this.bloomFilter == null) {
            if (other.bloomFilter != null) {
                return false;
            }
        } else if (this.bloomFilter.equals(other.bloomFilter)) {
            return false;
        }
        if (this.label != other.label) {
            return false;
        }
        return true;
    }
}
