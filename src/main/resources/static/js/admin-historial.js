document.addEventListener("DOMContentLoaded", () => {

    const cards = [...document.querySelectorAll(".registro-card")];

    const search = document.getElementById("registroSearch");
    const pagination = document.getElementById("registroPagination");
    const range = document.getElementById("registroRange");
    const total = document.getElementById("registroTotal");
    const empty = document.getElementById("registroEmpty");

    const buttons = [...document.querySelectorAll("[data-registro-filter]")];

    const PER_PAGE = 3;

    let activeFilter = "all";
    let currentPage = 1;
    let filteredCards = [...cards];

    function normalize(value) {
        return (value || "")
            .toString()
            .toLocaleLowerCase("es")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim();
    }

    function filterRecords() {

        const term = normalize(search?.value);

        filteredCards = cards.filter(card => {

            const status = card.dataset.status;
            const content = normalize(card.dataset.search);

            const matchesStatus =
                activeFilter === "all" ||
                activeFilter === status;

            const matchesSearch =
                content.includes(term);

            return matchesStatus && matchesSearch;
        });

        currentPage = 1;

        render();
    }

    function createButton(label, page, options = {}) {

        const button = document.createElement("button");

        button.type = "button";
        button.className = "page-btn";
        button.textContent = label;

        button.disabled = !!options.disabled;

        if (options.active) {
            button.classList.add("active");
            button.setAttribute("aria-current", "page");
        }

        if (!button.disabled) {
            button.addEventListener("click", () => {

                currentPage = page;

                render();

                document.getElementById("registroList")
                    ?.scrollIntoView({
                        behavior: "smooth",
                        block: "start"
                    });

            });
        }

        return button;
    }

    function renderPagination(totalPages) {

        if (!pagination) return;

        pagination.innerHTML = "";

        pagination.appendChild(
            createButton("«", 1, {
                disabled: currentPage === 1
            })
        );

        pagination.appendChild(
            createButton("‹", currentPage - 1, {
                disabled: currentPage === 1
            })
        );

        if (totalPages <= 7) {

            for (let i = 1; i <= totalPages; i++) {

                pagination.appendChild(
                    createButton(String(i), i, {
                        active: currentPage === i
                    })
                );

            }

        } else {

            pagination.appendChild(
                createButton("1", 1, {
                    active: currentPage === 1
                })
            );

            const start = Math.max(2, currentPage - 1);
            const end = Math.min(totalPages - 1, currentPage + 1);

            if (start > 2) {
                const dots = document.createElement("span");
                dots.className = "page-ellipsis";
                dots.textContent = "…";
                pagination.appendChild(dots);
            }

            for (let i = start; i <= end; i++) {

                pagination.appendChild(
                    createButton(String(i), i, {
                        active: currentPage === i
                    })
                );

            }

            if (end < totalPages - 1) {
                const dots = document.createElement("span");
                dots.className = "page-ellipsis";
                dots.textContent = "…";
                pagination.appendChild(dots);
            }

            pagination.appendChild(
                createButton(String(totalPages), totalPages, {
                    active: currentPage === totalPages
                })
            );

        }

        pagination.appendChild(
            createButton("›", currentPage + 1, {
                disabled: currentPage === totalPages
            })
        );

        pagination.appendChild(
            createButton("»", totalPages, {
                disabled: currentPage === totalPages
            })
        );

    }

    function render() {

        const totalRecords = filteredCards.length;

        const totalPages = Math.max(
            1,
            Math.ceil(totalRecords / PER_PAGE)
        );

        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        cards.forEach(card => {
            card.classList.add("registro-oculto");
        });

        const start = (currentPage - 1) * PER_PAGE;
        const end = start + PER_PAGE;

        filteredCards.slice(start, end).forEach(card => {
            card.classList.remove("registro-oculto");
        });

        if (total) {
            total.textContent = totalRecords;
        }

        if (range) {

            range.textContent = totalRecords === 0
                ? "0–0"
                : `${start + 1}–${Math.min(end, totalRecords)}`;

        }

        if (empty) {
            empty.style.display =
                totalRecords === 0 ? "block" : "none";
        }

        renderPagination(totalPages);

        updateCounters();

    }

    function updateCounters() {

        buttons.forEach(button => {

            const filter = button.dataset.registroFilter;

            const count = filter === "all"
                ? cards.length
                : cards.filter(card =>
                    card.dataset.status === filter
                ).length;

            const counter = button.querySelector(".filter-count");

            if (counter) {
                counter.textContent = count;
            }

        });

    }

    buttons.forEach(button => {

        button.addEventListener("click", () => {

            activeFilter = button.dataset.registroFilter;

            buttons.forEach(btn => {
                btn.classList.toggle("active", btn === button);
                btn.setAttribute(
                    "aria-pressed",
                    String(btn === button)
                );
            });

            filterRecords();

        });

    });

    search?.addEventListener("input", filterRecords);

    updateCounters();
    render();

});
