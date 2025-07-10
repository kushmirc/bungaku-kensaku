// Add interactivity
document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.querySelector('.search-input');
    const searchScopeRadios = document.querySelectorAll('input[name="searchScope"]');
    
    // Handle search scope radio button changes
    searchScopeRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            // Query bookSelections each time to ensure we get the current element
            const bookSelections = document.querySelector('.book-selections');
            if (bookSelections) {
                if (this.value === 'specific') {
                    bookSelections.style.display = 'flex';
                } else {
                    bookSelections.style.display = 'none';
                    // Close all dropdowns when hiding book selections
                    document.querySelectorAll('.suggestion-chip.dropdown-open').forEach(chip => {
                        chip.classList.remove('dropdown-open');
                    });
                }
            }
        });
    });

    // Handle dropdown chip clicks
    document.addEventListener('click', function(e) {
        const chip = e.target.closest('.suggestion-chip.has-dropdown');
        const isDropdownClick = e.target.closest('.dropdown-menu');
        
        // If clicking on a dropdown chip (but not inside the menu)
        if (chip && !isDropdownClick) {
            e.stopPropagation();
            
            // Close other dropdowns
            document.querySelectorAll('.suggestion-chip.dropdown-open').forEach(otherChip => {
                if (otherChip !== chip) {
                    otherChip.classList.remove('dropdown-open');
                }
            });
            
            // Toggle this dropdown
            chip.classList.toggle('dropdown-open');
        }
        // If clicking outside all dropdowns
        else if (!isDropdownClick) {
            document.querySelectorAll('.suggestion-chip.dropdown-open').forEach(chip => {
                chip.classList.remove('dropdown-open');
            });
        }
    });

    // Handle checkbox logic - "all volumes" controls individual volumes
    document.querySelectorAll('.dropdown-menu').forEach(menu => {
        const checkboxes = menu.querySelectorAll('input[type="checkbox"]');
        const allVolumesCheckbox = checkboxes[0]; // First checkbox is "all volumes"
        const volumeCheckboxes = Array.from(checkboxes).slice(1); // Rest are individual volumes
        const parentChip = menu.closest('.suggestion-chip');

        // Function to update chip selected state
        function updateChipSelectedState() {
            const anyChecked = Array.from(checkboxes).some(cb => cb.checked);
            if (anyChecked) {
                parentChip.classList.add('selected');
            } else {
                parentChip.classList.remove('selected');
            }
        }

        // When "all volumes" is clicked
        allVolumesCheckbox.addEventListener('change', function() {
            volumeCheckboxes.forEach(checkbox => {
                checkbox.checked = this.checked;
            });
            updateChipSelectedState();
        });

        // When individual volumes are clicked
        volumeCheckboxes.forEach(checkbox => {
            checkbox.addEventListener('change', function() {
                // Update "all volumes" based on individual selections
                const allChecked = volumeCheckboxes.every(cb => cb.checked);
                const someChecked = volumeCheckboxes.some(cb => cb.checked);
                
                allVolumesCheckbox.checked = allChecked;
                allVolumesCheckbox.indeterminate = someChecked && !allChecked;
                updateChipSelectedState();
            });
        });
    });

    // Prevent dropdown from closing when clicking inside menu
    document.querySelectorAll('.dropdown-menu').forEach(menu => {
        menu.addEventListener('click', function(e) {
            e.stopPropagation();
        });
    });

    // First, remove the required attribute so we can handle empty searches
    searchInput.removeAttribute('required');
    
    // Function to show loading state
    function showLoadingState() {
        const resultsSection = document.querySelector('.search-results');
        const resultsContent = document.querySelector('.results-content');
        const resultCount = document.querySelector('.result-count');
        const searchScopeInfo = document.querySelector('.search-scope-info');
        
        // Hide the result count and scope info
        if (resultCount) {
            resultCount.style.display = 'none';
        }
        if (searchScopeInfo) {
            searchScopeInfo.style.display = 'none';
        }
        
        // Show the results section
        resultsSection.style.display = 'block';
        
        // Create loading content
        const loadingHTML = `
            <div class="loading-container">
                <div class="orb-pulse"></div>
                <div class="loading-text">考察中...</div>
                <div class="loading-explanation">(この検索は通常の検索よりも深い分析を行います。しばらくお待ちください、通常約1分程度かかります)</div>
            </div>
        `;
        
        resultsContent.innerHTML = loadingHTML;
        
        // Scroll to search container (just below hero section) to draw a bit more attention when there's content
        document.querySelector('.search-container').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    
    // Function to show rate limit popup
    function showRateLimitPopup() {
        // Create popup overlay
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0, 0, 0, 0.5);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        `;
        
        // Create popup content
        const popup = document.createElement('div');
        popup.style.cssText = `
            background-color: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            max-width: 400px;
            text-align: center;
        `;
        
        popup.innerHTML = `
            <h3 style="color: #2c5aa0; margin-bottom: 15px;">検索制限に達しました</h3>
            <p style="color: #333; margin-bottom: 20px;">セッションあたりの最大検索回数（3回）に達しました。新しいセッションを開始してください。</p>
            <button style="
                background-color: #2c5aa0;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 5px;
                cursor: pointer;
                font-size: 16px;
            " onclick="this.closest('div').parentElement.remove()">閉じる</button>
        `;
        
        overlay.appendChild(popup);
        document.body.appendChild(overlay);
        
        // Close popup when clicking overlay
        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) {
                overlay.remove();
            }
        });
    }

    // Handle form submission
    const searchForm = document.querySelector('#search-form');
    searchForm.addEventListener('submit', function(e) {
        const query = searchInput.value.trim();
        
        // If query is empty, show helpful instructions
        if (!query) {
            e.preventDefault();
            const resultsSection = document.querySelector('.search-results');
            const resultsContent = document.querySelector('.results-content');
            const resultCount = document.querySelector('.result-count');
            
            // Hide the result count specifically
            if (resultCount) {
                resultCount.style.display = 'none';
            }
            
            // Also hide the search scope info when showing help text
            const searchScopeInfo = document.querySelector('.search-scope-info');
            if (searchScopeInfo) {
                searchScopeInfo.style.display = 'none';
            }
            
            resultsSection.style.display = 'block';
            
            const helpText = `検索を開始するには：<br><br>
1. 上の検索欄にご質問を入力してください<br>
&nbsp;&nbsp;&nbsp;例えば、「人生の意味についてどのように描かれていますか？」<br><br>
2. 「検索」ボタンをクリックしてください`;
            
            resultsContent.innerHTML = helpText;
            
            // Scroll to hero section (keeps search container fully visible)
            document.querySelector('.hero-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
        } else {
            // If there's a query, show loading state
            showLoadingState();
            // Include book selections in the form
            includeBookSelections();
            // Add hash to URL so page scrolls to search area after reload
            if (!window.location.hash) {
                const form = document.querySelector('#search-form');
                const currentAction = form.action;
                form.action = currentAction + '#search-container';
            }
        }
    });

    // Auto-expand textarea as user types
    function adjustTextareaHeight() {
        searchInput.style.height = 'auto'; // Reset height
        const scrollHeight = searchInput.scrollHeight;
        
        if (scrollHeight <= 300) { // Within max height
            searchInput.style.height = scrollHeight + 'px';
            searchInput.classList.remove('has-scroll');
        } else {
            searchInput.style.height = '300px';
            searchInput.classList.add('has-scroll');
        }
    }
    
    // Adjust height on input
    searchInput.addEventListener('input', adjustTextareaHeight);
    
    // Handle Enter key in search input (Ctrl+Enter to submit)
    searchInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            document.querySelector('#search-form').submit();
        }
    });
    
    // Initial height adjustment
    adjustTextareaHeight();
    
    // Initialize page state based on server-side values
    initializePageState();
    
    // Initialize multi-book chip selected states
    initializeChipStates();
    
    
    /**
     * Include book selections in the search form before submission.
     * This copies all checked book/series checkboxes into the form so they're submitted.
     */
    function includeBookSelections() {
        const form = document.querySelector('#search-form');
        const searchScope = document.querySelector('input[name="searchScope"]:checked').value;
        
        console.log('includeBookSelections called, searchScope:', searchScope);
        
        // Remove any existing book selection inputs from previous submissions (but preserve CSRF token)
        form.querySelectorAll('input[name^="book_"], input[name^="series_"]').forEach(input => {
            if (!input.closest('.search-options') && !input.closest('.book-selections') && !input.name.includes('csrf')) {
                input.remove();
            }
        });
        
        // If searching all books, no need to include book selections
        if (searchScope === 'all') {
            console.log('Search scope is "all", skipping book selections');
            return;
        }
        
        // Find all checked book and series checkboxes in the book-selections area
        const bookSelections = document.querySelector('.book-selections');
        if (bookSelections) {
            // Get checked checkboxes from dropdowns
            const checkedInputs = bookSelections.querySelectorAll('input[type="checkbox"]:checked');
            console.log('Found', checkedInputs.length, 'checked inputs:', checkedInputs);
            
            checkedInputs.forEach(input => {
                // Create a hidden input to include in the form
                const hiddenInput = document.createElement('input');
                hiddenInput.type = 'hidden';
                hiddenInput.name = input.name;
                hiddenInput.value = input.value;
                form.appendChild(hiddenInput);
                console.log('Added hidden input:', input.name, '=', input.value);
            });
            
            // Also handle selected single-book chips
            const selectedSingleBooks = bookSelections.querySelectorAll('.suggestion-chip.single-book.selected');
            selectedSingleBooks.forEach(chip => {
                const bookId = chip.dataset.bookId;
                if (bookId) {
                    const hiddenInput = document.createElement('input');
                    hiddenInput.type = 'hidden';
                    hiddenInput.name = `book_${bookId}`;
                    hiddenInput.value = bookId;
                    form.appendChild(hiddenInput);
                    console.log('Added single book selection:', hiddenInput.name, '=', bookId);
                }
            });
            
            const totalSelections = checkedInputs.length + selectedSingleBooks.length;
            console.log('Added', totalSelections, 'book/series selections to form');
        } else {
            console.log('No book-selections element found');
        }
    }
    
    /**
     * Initialize page state based on server-rendered values.
     * This ensures UI state matches the backend state after form submission.
     */
    function initializePageState() {
        const searchScopeSpecific = document.querySelector('input[name="searchScope"][value="specific"]');
        const bookSelections = document.querySelector('.book-selections');
        
        if (searchScopeSpecific && searchScopeSpecific.checked) {
            // If "specific" is selected, show book selections
            if (bookSelections) {
                bookSelections.style.display = 'flex';
                console.log('Initialized page with specific search scope - showing book selections');
            }
        } else {
            // If "all" is selected, hide book selections
            if (bookSelections) {
                bookSelections.style.display = 'none';
                console.log('Initialized page with all search scope - hiding book selections');
            }
        }
    }
    
    /**
     * Initialize chip selected states based on checkbox states.
     * This ensures chips show as selected if any checkboxes are already checked on page load.
     */
    function initializeChipStates() {
        document.querySelectorAll('.suggestion-chip.has-dropdown').forEach(chip => {
            const checkboxes = chip.querySelectorAll('input[type="checkbox"]');
            const anyChecked = Array.from(checkboxes).some(cb => cb.checked);
            if (anyChecked) {
                chip.classList.add('selected');
            }
        });
    }
    

    // Handle non-dropdown chip clicks (e.g., 青年対話)
    document.querySelectorAll('.suggestion-chip.single-book').forEach(chip => {
        chip.addEventListener('click', function() {
            // Toggle selection state
            this.classList.toggle('selected');
            console.log('Toggled selection for:', this.textContent, 'Selected:', this.classList.contains('selected'));
        });
    });

    
    // Handle clear results button
    const clearButton = document.querySelector('.clear-results');
    if (clearButton) {
        clearButton.addEventListener('click', function() {
            const resultsSection = document.querySelector('.search-results');
            const resultsContent = document.querySelector('.results-content');
            
            // Clear content and hide section
            resultsContent.textContent = '';
            resultsSection.style.display = 'none';
            
            // Clear search input
            document.querySelector('.search-input').value = '';
            // Reset height
            adjustTextareaHeight();
        });
    }

    // Check if rate limit message is present and show popup
    const rateLimitMessage = document.querySelector('.rate-limit-message');
    if (rateLimitMessage && rateLimitMessage.textContent.includes('セッションあたりの検索制限に達しました')) {
        // Hide the entire results section
        const resultsSection = document.querySelector('.search-results');
        if (resultsSection) {
            resultsSection.style.display = 'none';
        }
        // Show the popup
        showRateLimitPopup();
    }

});

// Toggle expand/collapse for search results
function toggleExpand(button) {
    // Navigate from button -> result-actions -> search-result-item -> find expandable-content
    const resultItem = button.closest('.search-result-item');
    const resultContent = resultItem.querySelector('.result-content');
    const expandText = button.querySelector('.expand-text');
    const collapseText = button.querySelector('.collapse-text');
    
    if (resultContent.classList.contains('collapsed')) {
        // Expand
        resultContent.classList.remove('collapsed');
        expandText.style.display = 'none';
        collapseText.style.display = 'inline';
    } else {
        // Collapse
        resultContent.classList.add('collapsed');
        expandText.style.display = 'inline';
        collapseText.style.display = 'none';
    }
}

// Open full text in new window with proper URL handling
function openFullText(link) {
    const path = link.getAttribute('data-path');
    const chunkId = link.getAttribute('data-chunk');
    
    if (path && chunkId) {
        // Construct the URL with the chunk anchor
        const url = path + '#chunk-' + chunkId;
        // Open in new window
        window.open(url, '_blank');
    }
}