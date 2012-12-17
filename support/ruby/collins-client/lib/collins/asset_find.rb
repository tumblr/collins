module Collins
  class Asset
    module Find
      # Find API parameters that are dates
      # @return [Array<String>] Date related query parameters
      DATE_PARAMS = [
        "createdAfter", "createdBefore", "updatedAfter", "updatedBefore"
      ]
      # Find API parameters that are not dates
      # This list exists so that when assets are being queries, we know what keys in the find hash
      # are attributes of the asset (such as hostname), and which are nort (such as sort or page).
      # @return [Array,<String>] Non-date related query parameters that are 'reserved'
      GENERAL_PARAMS = [
        "details", "tag", "type", "status", "page", "size", "sort", "state", "operation", "remoteLookup", "query",
        "sortField"
      ]
      # @return [Array<String>] DATE_PARAMS plus GENERAL_PARAMS
      ALL_PARAMS = DATE_PARAMS + GENERAL_PARAMS
      class << self
        def to_a
          Collins::Asset::Find::ALL_PARAMS
        end
        def valid? key
          to_a.include?(key.to_s)
        end
      end
    end
  end
end
